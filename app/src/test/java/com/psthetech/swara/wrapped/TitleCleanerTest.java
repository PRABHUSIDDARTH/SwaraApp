package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TitleCleanerTest {
    @Test
    public void stripsSiteTagsKeepingCase() {
        assertEquals("Kannukulla", TitleCleaner.clean("Kannukulla - MassTamilan.dev"));
        assertEquals("Arabic Kuthu - Halamithi Habibo", TitleCleaner.clean("Arabic Kuthu - Halamithi Habibo - MassTamilan.so"));
        assertEquals("Let Me Down Slowly", TitleCleaner.clean("Let Me Down Slowly(PagalWorldl)"));
        assertEquals("Ra Ra Reddy I Am Ready", TitleCleaner.clean("[iSongs.info] 02 - Ra Ra Reddy I Am Ready"));
        assertEquals("Illuminati", TitleCleaner.clean("Illuminati - oruTamilsong.com"));
        assertEquals("Parayuvaan", TitleCleaner.clean("Parayuvaan-(Fun2Desi.Com)"));
        assertEquals("Alaakaa Loova", TitleCleaner.clean("Alaakaa Loova - Masstamilan.MY"));
        assertEquals("KALYANI", TitleCleaner.clean("KALYANI(KoshalWorld.Com)"));
    }

    @Test
    public void artistTagsStripped() {
        assertEquals("Sai Abhyankkar, Jonita Gandhi", TitleCleaner.clean("Sai Abhyankkar, Jonita Gandhi - MassTamilan.dev"));
        assertEquals("", TitleCleaner.clean("PagalWorldl.Com"));
    }

    @Test
    public void plainTitlesUntouched() {
        assertEquals("Lottery", TitleCleaner.clean("Lottery"));
        assertEquals("Aura 10/10", TitleCleaner.clean("Aura 10/10 - MassTamilan"));
        assertEquals("Song (Version 2)", TitleCleaner.clean("Song (Version 2)"));
        assertEquals("", TitleCleaner.clean(null));
    }
}
