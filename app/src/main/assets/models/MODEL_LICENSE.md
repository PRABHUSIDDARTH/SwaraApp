# Korokae model weights license

`korokae_v1.bin` is **not** covered by the Apache License 2.0 that applies to the rest of Swara.

The weights were trained on the [MUSDB18](https://sigsep.github.io/datasets/musdb.html) dataset. MUSDB18 is provided for educational purposes only and must not be used for any commercial purpose without the express permission of the copyright holders. Parts of it are licensed under Creative Commons BY-NC-SA.

You may use, copy and redistribute these weights for **non-commercial and educational purposes only**, provided you keep this notice and the citation below. Do not sell the weights or an app that bundles them, and do not use them in a paid or ad-supported product, without first getting permission from the MUSDB18 copyright holders or retraining the model on data you have the rights to.

The weights contain no audio from the dataset.

To ship Swara commercially, either remove this file (Korokae mode then reports that the model is unavailable) or replace it with a model trained on licensed data using `ml/korokae/`.

## Citation

Zafar Rafii, Antoine Liutkus, Fabian-Robert Stoter, Stylianos Ioannis Mimilakis and Rachel Bittner. *The MUSDB18 corpus for music separation*, 2017. https://doi.org/10.5281/zenodo.1117372
