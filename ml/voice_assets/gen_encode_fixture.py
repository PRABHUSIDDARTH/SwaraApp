"""Writes app/src/test/resources/voice/encode_fixture.json: library-style strings -> ids from the
Hugging Face `tokenizers` Moonshine tokenizer (no special tokens). Run: python -I gen_encode_fixture.py"""
import json
import pathlib

from tokenizers import Tokenizer

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOK = ROOT / "app/src/main/assets/voice/moonshine/tokenizer.json"
OUT = ROOT / "app/src/test/resources/voice/encode_fixture.json"

BASE = [
    "Kaavaalaa", "Anirudh Ravichander", "Thalapathy", "Azhagu", "Rowdy Baby", "Vaathi Coming",
    "Arabic Kuthu", "Sid Sriram", "A.R. Rahman", "Yuvan Shankar Raja", "Hukum", "Jailer",
    "Ranjithame", "Chaleya", "Why This Kolaveri Di", "Vikram", "Master", "Beast", "Leo",
    "Naa Ready", "Badass", "Oo Antava", "Srivalli", "Butta Bomma", "Ala Vaikunthapurramuloo",
    "Arijit Singh", "Shreya Ghoshal", "Kesariya", "Tum Hi Ho", "Channa Mereya", "Apna Bana Le",
    "Tere Vaaste", "Kun Faya Kun", "Jai Ho", "Chaiyya Chaiyya", "Dil Se Re", "Ae Dil Hai Mushkil",
    "Bohemian Rhapsody", "Stairway to Heaven", "Hotel California", "Shape of You", "Blinding Lights",
    "Bad Guy", "Levitating", "Believer", "Thunder", "Counting Stars", "Radioactive", "Demons",
    "Imagine Dragons", "Coldplay", "The Weeknd", "Taylor Swift", "Ed Sheeran", "Billie Eilish",
    "Ilaiyaraaja", "Harris Jayaraj", "Santhosh Narayanan", "G. V. Prakash Kumar", "Hiphop Tamizha",
    "Sam C.S.", "Devi Sri Prasad", "S. P. Balasubrahmanyam", "K. S. Chithra", "Haricharan",
    "Karthik", "Chinmayi", "Sid Sriram & Chinmayi", "Anirudh feat. Jonita Gandhi", "Vijay Antony",
    "Ghibran", "Thaman S", "Mani Sharma", "Pritam", "Vishal-Shekhar", "Amit Trivedi", "Badshah",
    "Yo Yo Honey Singh", "Diljit Dosanjh", "Sidhu Moose Wala", "AP Dhillon", "Guru Randhawa",
    "Neele Neele Ambar Par", "Lag Ja Gale", "Kabhi Kabhi", "Mere Sapno Ki Rani", "Pehla Nasha",
    "Munbe Vaa", "Vennilave Vennilave", "Kannazhaga", "Nenjukkul Peidhidum", "Mustafa Mustafa",
    "Urvasi Urvasi", "Muqabla", "Ennavale", "Minnale", "Thendral Vanthu", "Aaromale", "Unnale Unnale",
    "Mella Pesungal", "Kadhal Rojave", "Roja Janeman", "Chinna Chinna Aasai", "Poongatrae",
    "Raja Rajathi Raja", "Oru Manam", "Vaa Vaathi", "Thee Thalapathy", "Ranjithame Ranjithame",
    "Arabic Kuthu Halamithi Habibo", "Jolly O Gymkhana", "Naan Pizhai", "Kaathuvaakula Rendu Kaadhal",
    "Rakita Rakita Rakita", "Pachai Nirame", "Hey Minnale", "Otha Sollaala", "Jimikki Kammal",
    "Kolaveri", "Vaadi Pulla Vaadi", "Surviva", "Aalaporaan Thamizhan", "Singappenney",
    "Maamadura", "Verithanam", "Bigil", "Master the Blaster", "Vaathi Raid", "Mersal Arasan",
    "Aaluma Doluma", "Danga Maari", "Marana Mass", "Chellamma", "Don't Stop Believin'",
    "It's My Life", "Rock 'n' Roll", "Mr. Brightside", "Lose Yourself", "Under Pressure",
    "(I Can't Get No) Satisfaction", "Smells Like Teen Spirit", "Livin' on a Prayer",
    "What's Up?", "Who Let the Dogs Out?", "Hello!", "Yes, Sir", "Rock & Roll", "Hit-Me-Baby",
    "Track 01", "Track 12", "2 Pac", "50 Cent", "21 Guns", "7 Rings", "99 Problems", "1999",
    "Song No. 3", "Part II", "Vol. 2", "MCMXC a.D.", "U2", "AC/DC", "ABBA", "ZAYN", "BTS", "NF",
    "iPhone Ringtone", "eminem", "EMINEM", "LOUD", "MixedCase Title", "camelCaseWord",
    "ALL CAPS TITLE", "lowercase title here",
    "play", "pause", "resume", "stop", "next", "previous", "skip", "shuffle", "repeat",
    "volume up", "volume down", "play kaavaalaa", "play something by anirudh", "next song",
    "go back", "louder", "quieter", "mute", "unmute", "play my playlist", "add to queue",
    "Lofi Beats", "Chill Mix", "Workout Mix", "Road Trip", "Late Night Drive", "Monsoon Melodies",
    "Naatu Naatu", "Kaavaalaa - Jailer", "Hukum (From \"Jailer\")", "Kesariya [From Brahmastra]",
    "Artist - Song Title (Remix)", "Song_Name_With_Underscores", "dots.in.name.mp3",
    "Café del Mar", "Beyoncé", "Mötley Crüe", "Sigur Rós", "Björk", "Zoë", "Señorita", "Naïve",
    "Shakira — Waka Waka", "Dil Chahta Hai – Title Track", "“Quoted Title”", "‘Single’ Quote",
]
NONASCII = [
    "तुम ही हो", "कुन फाया कुन", "अरिजीत सिंह", "केसरिया", "चैयां चैयां", "श्रेया घोषाल",
    "வா வாத்தி", "காவாலா", "அனிருத் ரவிச்சந்தர்", "ஏ. ஆர். ரகுமான்", "என்னவளே",
    "తెలుగు పాట", "ಕನ್ನಡ ಹಾಡು", "മലയാളം", "বাংলা গান", "ਪੰਜਾਬੀ ਗੀਤ", "你好世界", "こんにちは", "안녕하세요",
    "Привет мир", "مرحبا", "🎵", "Party 🎉 Time", "Love ❤️ Song", "𝄞 Clef", "😀😃😄",
]


def variants(s):
    return [s, " " + s, s + " ", "  " + s, s.replace(" ", "  ")]


def main():
    tk = Tokenizer.from_file(str(TOK))
    strings = []
    seen = set()

    def add(s):
        if s not in seen:
            seen.add(s)
            strings.append(s)

    for s in BASE:
        add(s)
        add(" " + s)
    for s in BASE[:40]:
        add(s + " ")
        add(s.replace(" ", "  "))
        add(s.lower())
        add(s.upper())
    for s in NONASCII:
        add(s)
        add(" " + s)
    add("")
    add(" ")
    add("   ")
    cases = [{"text": s, "ids": tk.encode(s, add_special_tokens=False).ids} for s in strings]
    assert len(cases) >= 200, len(cases)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(cases, ensure_ascii=False, indent=0), encoding="utf-8")
    print(len(cases), "cases ->", OUT)


main()
