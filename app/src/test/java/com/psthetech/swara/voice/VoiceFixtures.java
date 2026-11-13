package com.psthetech.swara.voice;

import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.List;

/** Shared ~60-song library for voice matcher / executor tests. */
public final class VoiceFixtures {

    public static final long ALBUM_JAILER = 100L;
    public static final long ALBUM_ROJA_TAMIL = 200L;
    public static final long ALBUM_ROJA_HINDI = 201L;
    public static final long ALBUM_SCENE = 300L;
    /** An album named exactly like the artist "Hariharan". */
    public static final long ALBUM_HARIHARAN = 600L;

    public static final long KAAVAALAA_ID = 1L;
    public static final int RAHMAN_SONG_COUNT = 12;
    public static final int AMEEN_SONG_COUNT = 2;
    public static final int SCENE_SONG_COUNT = 4;

    public static final Song KAAVAALAA = new Song(KAAVAALAA_ID, "Kaavaalaa", "Anirudh Ravichander",
            "Jailer", ALBUM_JAILER, 200000, 1, 2023, 1L);

    private VoiceFixtures() {}

    private static long nextId = 1000;

    public static List<Song> library() {
        nextId = 1000;
        List<Song> l = new ArrayList<>();
        l.add(KAAVAALAA);
        l.add(new Song(2L, "Hukum", "Anirudh Ravichander", "Jailer", ALBUM_JAILER, 190000, 2, 2023, 1L));

        // Roja (Tamil 1992), track numbers out of order
        String tamil = "Roja (Original Motion Picture Soundtrack)";
        int[] order = {4, 1, 6, 3, 2, 5};
        String[] t = {"Chinna Chinna Aasai", "Kadhal Rojave", "Pudhu Vellai Mazhai",
                "Rukkumani", "Vidudhalai", "Tamizha Tamizha"};
        for (int i = 0; i < 6; i++) {
            l.add(new Song(2000L + i, t[i], "S. P. Balasubrahmanyam", tamil, ALBUM_ROJA_TAMIL, 250000, order[i], 1992, 5L));
        }
        // Roja (Hindi 1993)
        String hindi = "Roja - MassTamilan.com";
        for (int i = 0; i < 3; i++) {
            l.add(new Song(2100L + i, new String[]{"Yeh Haseen Wadiyan", "Dil Hai Chhota Sa", "Bharat Hum Ko"}[i],
                    "Hariharan", hindi, ALBUM_ROJA_HINDI, 240000, i + 1, 1993, 6L));
        }

        // Scene, 4 tracks
        String[] sc = {"Neon Harbor", "Glass Orchard", "Paper Lanterns", "Midnight Ferry"};
        for (int i = 0; i < 4; i++) {
            l.add(new Song(3000L + i, sc[i], "Lumen Drift", "Scene", ALBUM_SCENE, 210000, i + 1, 2019, 7L));
        }

        // Rahman credit variants: 4 + 3 + 3 + 2 = 12
        String[] rahmanTitles = {"Jiya Jale", "Chaiyya Chaiyya", "Dil Se Re", "Tu Hi Re", "Kun Faya Kun",
                "Khwaja Mere", "Vande Mataram", "Mustafa", "Urvasi", "Humma Humma", "Ye Jo Des Hai", "Taal Se Taal"};
        String[] credits = {"A.R. Rahman", "A.R. Rahman", "A.R. Rahman", "A.R. Rahman",
                "A. R. Rahman", "A. R. Rahman", "A. R. Rahman",
                "AR Rahman", "AR Rahman", "AR Rahman",
                "A.R. Rahman, Shreya Ghoshal", "A.R. Rahman, Shreya Ghoshal"};
        for (int i = 0; i < 12; i++) {
            l.add(new Song(4000L + i, rahmanTitles[i], credits[i], "Rahman Hits " + (i % 3), 400L + (i % 3),
                    260000, i + 1, 2000 + i, 8L));
        }
        l.add(new Song(4100L, "Quiet Evening", "Ameen Rahman", "Evenings", 410L, 180000, 1, 2015, 9L));
        l.add(new Song(4101L, "Morning Dew", "Ameen Rahman", "Evenings", 410L, 180000, 2, 2015, 9L));
        l.add(new Song(4200L, "Rahman Theme", "Studio Ensemble", "Themes", 420L, 150000, 1, 2010, 10L));

        // Album that shares its name with an artist
        l.add(new Song(4300L, "Tribute One", "Tribute Band", "Hariharan", ALBUM_HARIHARAN, 200000, 1, 2020, 12L));
        l.add(new Song(4301L, "Tribute Two", "Tribute Band", "Hariharan", ALBUM_HARIHARAN, 200000, 2, 2020, 12L));

        // Filler
        String[][] filler = {
                {"Blinding Lights", "The Weeknd", "After Hours"}, {"Shape of You", "Ed Sheeran", "Divide"},
                {"Bohemian Rhapsody", "Queen", "A Night at the Opera"}, {"Hotel California", "Eagles", "Hotel California"},
                {"Imagine", "John Lennon", "Imagine"}, {"Thriller", "Michael Jackson", "Thriller"},
                {"Smells Like Teen Spirit", "Nirvana", "Nevermind"}, {"Rolling in the Deep", "Adele", "21"},
                {"Lose Yourself", "Eminem", "8 Mile"}, {"Billie Jean", "Michael Jackson", "Thriller"},
                {"Yellow", "Coldplay", "Parachutes"}, {"Fix You", "Coldplay", "X&Y"},
                {"Numb", "Linkin Park", "Meteora"}, {"One More Time", "Daft Punk", "Discovery"},
                {"Get Lucky", "Daft Punk", "Random Access Memories"}, {"Happy", "Pharrell Williams", "G I R L"},
                {"Uptown Funk", "Mark Ronson", "Uptown Special"}, {"Shallow", "Lady Gaga", "A Star Is Born"},
                {"Perfect", "Ed Sheeran", "Divide"}, {"Stay", "Justin Bieber", "Stay"},
                {"Levitating", "Dua Lipa", "Future Nostalgia"}, {"Bad Guy", "Billie Eilish", "When We All Fall Asleep"},
                {"Sunflower", "Post Malone", "Spider-Verse"}, {"Counting Stars", "OneRepublic", "Native"},
                {"Radioactive", "Imagine Dragons", "Night Visions"}, {"Believer", "Imagine Dragons", "Evolve"},
                {"Mad World", "Gary Jules", "Donnie Darko"}, {"Wonderwall", "Oasis", "Morning Glory"},
                {"Africa", "Toto", "Toto IV"}, {"Take On Me", "a-ha", "Hunting High and Low"},
                {"Vaathi Coming", "Anirudh Ravichander", "Master"}, {"Why This Kolaveri Di", "Anirudh Ravichander", "3"},
                {"Mallipoo", "Madhushree", "Vendhu Thanindhathu Kaadu"}, {"Naatu Naatu", "Rahul Sipligunj", "RRR"},
                {"Kesariya", "Arijit Singh", "Brahmastra"}, {"Tum Hi Ho", "Arijit Singh", "Aashiqui 2"},
                {"Channa Mereya", "Arijit Singh", "Ae Dil Hai Mushkil"}, {"Senorita", "Farhan Akhtar", "ZNMD"},
                {"Kal Ho Naa Ho", "Sonu Nigam", "Kal Ho Naa Ho"}
        };
        for (String[] f : filler) {
            l.add(new Song(nextId++, f[0], f[1], f[2], 5000L + Math.abs(f[2].hashCode() % 1000), 200000, 1, 2010, 11L));
        }
        return l;
    }
}
