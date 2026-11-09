package com.psthetech.swara.korokae;

/**
 * Streaming, causal vocal remover. Pure Java, allocation-free per frame.
 *
 * Feed interleaved stereo float samples with {@link #process}; output is delayed by
 * exactly {@code nFft - 1} frames (~23 ms at 44.1 kHz). The mask network runs on
 * the mid channel's log-magnitude spectrum and the mask is applied to both channels, so
 * stereo image and phase are preserved.
 *
 * The {@code mix} parameter (0..1) crossfades between the original signal (0) and the
 * instrumental estimate (1), allowing click-free enable/disable.
 *
 * Not thread-safe: use from the audio rendering thread only.
 */
public final class KorokaeEngine {

    private final KorokaeModel m;
    private final int n;
    private final int hop;
    private final int bins;
    private final int hidden;

    private final float[] window;
    private final float[] cos;
    private final float[] sin;
    private final int[] bitrev;

    // Rolling analysis input (last n samples per channel) and how many new samples are queued.
    private final float[] inL;
    private final float[] inR;
    private int filled;

    // Overlap-add accumulators.
    private final float[] olaL;
    private final float[] olaR;

    // Output FIFO of hop-sized blocks (interleaved stereo).
    private final float[] outBuf;
    private int outRead;
    private int outAvail;

    // FFT scratch.
    private final float[] reL;
    private final float[] imL;
    private final float[] reR;
    private final float[] imR;
    private final float[] reM;
    private final float[] imM;

    // Network scratch.
    private final float[] feat;
    private final float[] x;
    private final float[][] h;
    private final float[] gi;
    private final float[] gh;
    private final float[] mask;

    private float mix = 1f;
    private float targetMix = 1f;

    public KorokaeEngine(KorokaeModel model) {
        this.m = model;
        this.n = model.nFft;
        this.hop = model.hop;
        this.bins = model.bins;
        this.hidden = model.hidden;

        window = new float[n];
        for (int i = 0; i < n; i++) {
            window[i] = (float) Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * i / n));
        }
        cos = new float[n / 2];
        sin = new float[n / 2];
        for (int i = 0; i < n / 2; i++) {
            cos[i] = (float) Math.cos(-2 * Math.PI * i / n);
            sin[i] = (float) Math.sin(-2 * Math.PI * i / n);
        }
        bitrev = new int[n];
        int bitsCount = Integer.numberOfTrailingZeros(n);
        for (int i = 0; i < n; i++) {
            bitrev[i] = Integer.reverse(i) >>> (32 - bitsCount);
        }

        inL = new float[n];
        inR = new float[n];
        olaL = new float[n];
        olaR = new float[n];
        outBuf = new float[2 * hop];
        reL = new float[n];
        imL = new float[n];
        reR = new float[n];
        imR = new float[n];
        reM = new float[n];
        imM = new float[n];
        feat = new float[bins];
        x = new float[hidden];
        h = new float[model.layers][hidden];
        gi = new float[3 * hidden];
        gh = new float[3 * hidden];
        mask = new float[bins];
        reset();
    }

    /** Output latency in sample frames. */
    public int getLatencyFrames() {
        return n - 1;
    }

    /** Sets the wet amount (0 = original, 1 = instrumental). Ramped per hop to avoid clicks. */
    public void setMix(float value) {
        targetMix = Math.max(0f, Math.min(1f, value));
    }

    public float getMix() {
        return targetMix;
    }

    /** Jumps the wet amount immediately (e.g. on flush/seek). */
    public void snapMix() {
        mix = targetMix;
    }

    public void reset() {
        java.util.Arrays.fill(inL, 0f);
        java.util.Arrays.fill(inR, 0f);
        java.util.Arrays.fill(olaL, 0f);
        java.util.Arrays.fill(olaR, 0f);
        for (float[] layer : h) java.util.Arrays.fill(layer, 0f);
        filled = n - hop; // pre-roll so the first frame fires after one hop
        outRead = 0;
        outAvail = 0;
        mix = targetMix;
    }

    /**
     * Processes {@code frames} stereo frames from {@code in} (interleaved) into {@code out}.
     * Both arrays hold at least {@code 2 * frames} floats; they may be the same array.
     */
    public void process(float[] in, int inOffset, float[] out, int outOffset, int frames) {
        for (int f = 0; f < frames; f++) {
            float l = in[inOffset + 2 * f];
            float r = in[inOffset + 2 * f + 1];
            inL[filled] = l;
            inR[filled] = r;
            filled++;
            if (filled == n) {
                runFrame();
                System.arraycopy(inL, hop, inL, 0, n - hop);
                System.arraycopy(inR, hop, inR, 0, n - hop);
                filled = n - hop;
            }
            if (outAvail > 0) {
                out[outOffset + 2 * f] = outBuf[2 * outRead];
                out[outOffset + 2 * f + 1] = outBuf[2 * outRead + 1];
                outRead++;
                outAvail--;
            } else {
                out[outOffset + 2 * f] = 0f;
                out[outOffset + 2 * f + 1] = 0f;
            }
        }
    }

    private void runFrame() {
        for (int i = 0; i < n; i++) {
            float w = window[i];
            reL[i] = inL[i] * w;
            imL[i] = 0f;
            reR[i] = inR[i] * w;
            imR[i] = 0f;
            reM[i] = 0.5f * (reL[i] + reR[i]);
            imM[i] = 0f;
        }
        fft(reL, imL);
        fft(reR, imR);
        fft(reM, imM);

        for (int k = 0; k < bins; k++) {
            float mag = (float) Math.sqrt(reM[k] * reM[k] + imM[k] * imM[k]);
            feat[k] = ((float) Math.log1p(mag) - m.mean[k]) / m.std[k];
        }
        computeMask();

        float startMix = mix;
        float endMix = targetMix;

        // Apply mask, rebuild conjugate-symmetric spectrum, inverse FFT via forward FFT of conj.
        applyMaskAndInverse(reL, imL);
        applyMaskAndInverse(reR, imR);

        float invN = 1f / n;
        for (int i = 0; i < n; i++) {
            olaL[i] += reL[i] * invN * window[i];
            olaR[i] += reR[i] * invN * window[i];
        }
        // The first hop of the accumulator is now complete; it covers the same input samples
        // as inL/inR[0..hop) (the oldest hop of the current analysis window).
        for (int i = 0; i < hop; i++) {
            float t = startMix + (endMix - startMix) * (i + 1) / hop;
            outBuf[2 * i] = t * olaL[i] + (1f - t) * inL[i];
            outBuf[2 * i + 1] = t * olaR[i] + (1f - t) * inR[i];
        }
        mix = endMix;
        System.arraycopy(olaL, hop, olaL, 0, n - hop);
        System.arraycopy(olaR, hop, olaR, 0, n - hop);
        java.util.Arrays.fill(olaL, n - hop, n, 0f);
        java.util.Arrays.fill(olaR, n - hop, n, 0f);
        outRead = 0;
        outAvail = hop;
    }

    private void applyMaskAndInverse(float[] re, float[] im) {
        for (int k = 0; k < bins; k++) {
            re[k] *= mask[k];
            im[k] = -im[k] * mask[k]; // conjugate for inverse-via-forward FFT
        }
        for (int k = bins; k < n; k++) {
            re[k] = re[n - k];
            im[k] = -im[n - k];
        }
        fft(re, im);
        // Result real part is the time signal (times n); imaginary part is ~0.
    }

    private void computeMask() {
        // Input projection + ReLU.
        for (int j = 0; j < hidden; j++) {
            float s = m.inB[j];
            int row = j * bins;
            for (int k = 0; k < bins; k++) s += m.inW[row + k] * feat[k];
            x[j] = s > 0f ? s : 0f;
        }
        float[] layerIn = x;
        for (int l = 0; l < m.layers; l++) {
            float[] hl = h[l];
            matVec(m.wIh[l], m.bIh[l], layerIn, gi);
            matVec(m.wHh[l], m.bHh[l], hl, gh);
            for (int j = 0; j < hidden; j++) {
                float r = sigmoid(gi[j] + gh[j]);
                float z = sigmoid(gi[hidden + j] + gh[hidden + j]);
                float nn = (float) Math.tanh(gi[2 * hidden + j] + r * gh[2 * hidden + j]);
                hl[j] = (1f - z) * nn + z * hl[j];
            }
            layerIn = hl;
        }
        for (int k = 0; k < bins; k++) {
            float s = m.outB[k];
            int row = k * hidden;
            for (int j = 0; j < hidden; j++) s += m.outW[row + j] * layerIn[j];
            mask[k] = sigmoid(s);
        }
    }

    private void matVec(float[] w, float[] b, float[] v, float[] out) {
        int rows = out.length;
        int cols = v.length;
        for (int i = 0; i < rows; i++) {
            float s = b[i];
            int row = i * cols;
            for (int j = 0; j < cols; j++) s += w[row + j] * v[j];
            out[i] = s;
        }
    }

    private static float sigmoid(float v) {
        return 1f / (1f + (float) Math.exp(-v));
    }

    /** In-place iterative radix-2 forward FFT. */
    private void fft(float[] re, float[] im) {
        for (int i = 0; i < n; i++) {
            int j = bitrev[i];
            if (j > i) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            int half = size >> 1;
            int step = n / size;
            for (int start = 0; start < n; start += size) {
                for (int k = 0; k < half; k++) {
                    float wr = cos[k * step];
                    float wi = sin[k * step];
                    int a = start + k;
                    int b = a + half;
                    float tr = re[b] * wr - im[b] * wi;
                    float ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
    }
}
