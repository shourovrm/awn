#!/usr/bin/env python3
"""Build feature/learn/src/main/assets/snippets.json.

Ayah text comes from the Quran database (awn-data/published/quran/quran.sqlite). Hadith text is the
saying itself, cut out of the full Arabic report from the fawazahmed0/hadith-api edition
(ara-bukhari, ara-muslim) by a start phrase and an end phrase; the script fails when either phrase
is missing or the report number does not match. The English and Bangla renderings below are the
project's own short renderings and are drafts that need a reviewer.

Usage: AWN_DATA_ROOT=/path/to/awn-data python3 -I tools/build_snippets.py
"""

import json
import os
import re
import sqlite3
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data_paths
import snippet_text

REPOSITORY = Path(__file__).resolve().parent.parent
OUTPUT = REPOSITORY / "feature/learn/src/main/assets/snippets.json"
API = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/{edition}.json"

DIACRITICS = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿ]")
LETTER_FORMS = {"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي"}

# (surah, ayah, English, Bangla)
AYAHS = [
    (73, 4, "Recite the Quran slowly and clearly.", "কুরআন থেমে থেমে স্পষ্ট করে তিলাওয়াত করো।"),
    (75, 18, "When We have recited it, follow its recitation.", "আমি যখন তা পাঠ করি, তখন তুমি সেই পাঠের অনুসরণ করো।"),
    (54, 17, "We have made the Quran easy to remember. Is there anyone who will remember?", "আমি কুরআনকে স্মরণের জন্য সহজ করেছি। তাই কেউ কি স্মরণ রাখবে?"),
    (17, 106, "A Quran We have divided so that you may recite it to people slowly.", "আমি কুরআনকে খণ্ড খণ্ড করে নাযিল করেছি, যাতে তুমি থেমে থেমে মানুষের কাছে পাঠ করতে পারো।"),
    (73, 20, "So recite what is easy of the Quran.", "অতএব কুরআন থেকে যতটা সহজ হয় ততটা পাঠ করো।"),
    (96, 1, "Read in the name of your Lord who created.", "পড়ো তোমার প্রতিপালকের নামে, যিনি সৃষ্টি করেছেন।"),
    (15, 9, "It is We who sent down the Reminder, and We will protect it.", "আমিই এই স্মারক নাযিল করেছি এবং আমিই এর সংরক্ষণকারী।"),
    (2, 152, "Remember Me and I will remember you. Thank Me and do not be ungrateful.", "তোমরা আমাকে স্মরণ করো, আমিও তোমাদের স্মরণ করব। আমার কৃতজ্ঞ হও, অকৃতজ্ঞ হয়ো না।"),
    (13, 28, "Those who believe, and whose hearts find rest in the remembrance of Allah. In the remembrance of Allah hearts find rest.", "যারা ঈমান আনে এবং আল্লাহর স্মরণে যাদের অন্তর প্রশান্ত হয়। জেনে রাখো, আল্লাহর স্মরণেই অন্তর প্রশান্ত হয়।"),
    (33, 41, "You who believe, remember Allah often.", "হে ঈমানদারগণ, তোমরা আল্লাহকে বেশি বেশি স্মরণ করো।"),
    (20, 114, "Do not hurry with the Quran before its revelation is complete, and say: My Lord, increase me in knowledge.", "ওহি সম্পূর্ণ হওয়ার আগে কুরআন পাঠে তাড়াহুড়ো করো না, আর বলো: হে আমার প্রতিপালক, আমার জ্ঞান বাড়িয়ে দিন।"),
    (7, 204, "When the Quran is recited, listen to it and be silent, so that you may receive mercy.", "যখন কুরআন পাঠ করা হয়, তখন মন দিয়ে শোনো এবং চুপ থাকো, যাতে তোমাদের প্রতি দয়া করা হয়।"),
    (2, 2, "This is the Book in which there is no doubt, a guidance for those who are mindful of Allah.", "এটি সেই কিতাব, যাতে কোনো সন্দেহ নেই; এটি আল্লাহভীরুদের জন্য পথনির্দেশ।"),
    (17, 82, "We send down of the Quran what is a healing and a mercy for the believers.", "আমি কুরআনে এমন কিছু নাযিল করি, যা মুমিনদের জন্য আরোগ্য ও রহমত।"),
    (38, 29, "A blessed Book We sent down to you, so that they may reflect on its ayahs and those with understanding may take heed.", "এটি বরকতময় কিতাব, যা আমি তোমার প্রতি নাযিল করেছি, যাতে তারা এর আয়াত নিয়ে চিন্তা করে এবং বুদ্ধিমানেরা উপদেশ গ্রহণ করে।"),
    (47, 24, "Do they not reflect on the Quran, or are there locks on their hearts?", "তারা কি কুরআন নিয়ে চিন্তা করে না? নাকি তাদের অন্তরে তালা পড়ে আছে?"),
    (16, 98, "When you recite the Quran, seek refuge in Allah from the accursed Satan.", "তুমি যখন কুরআন পাঠ করো, তখন অভিশপ্ত শয়তান থেকে আল্লাহর আশ্রয় চাও।"),
    (87, 6, "We will make you recite, and you will not forget.", "আমি তোমাকে পাঠ করাব, ফলে তুমি ভুলে যাবে না।"),
    (29, 49, "Rather, it is clear ayahs in the hearts of those who were given knowledge.", "বরং এটি সুস্পষ্ট আয়াত, যা জ্ঞানপ্রাপ্তদের অন্তরে রয়েছে।"),
    (3, 191, "Those who remember Allah standing, sitting and lying on their sides.", "যারা দাঁড়িয়ে, বসে ও কাত হয়ে শুয়ে আল্লাহকে স্মরণ করে।"),
    (20, 14, "Establish the prayer for My remembrance.", "আমার স্মরণের জন্য সালাত কায়েম করো।"),
    (7, 205, "Remember your Lord within yourself, humbly and in awe, and without raising your voice.", "তোমার প্রতিপালককে মনে মনে স্মরণ করো বিনয় ও ভয়ের সঙ্গে, এবং উচ্চস্বর ছাড়া।"),
    (10, 57, "People, a counsel has come to you from your Lord, and a healing for what is in the hearts.", "হে মানুষ, তোমাদের কাছে তোমাদের প্রতিপালকের পক্ষ থেকে উপদেশ এসেছে এবং অন্তরে যা আছে তার আরোগ্য।"),
    (59, 21, "Had We sent this Quran down on a mountain, you would see it humbled and split apart out of fear of Allah.", "আমি যদি এই কুরআন কোনো পাহাড়ের ওপর নাযিল করতাম, তুমি দেখতে তা আল্লাহর ভয়ে বিনীত হয়ে ফেটে পড়ছে।"),
    (17, 9, "This Quran guides to what is most upright.", "এই কুরআন সেই পথ দেখায়, যা সবচেয়ে সরল।"),
    (75, 16, "Do not move your tongue with it to hurry it.", "তাড়াহুড়ো করে তা আয়ত্ত করতে জিহ্বা নাড়িও না।"),
    (55, 2, "He taught the Quran.", "তিনি কুরআন শিখিয়েছেন।"),
    (56, 77, "It is a noble Quran.", "নিশ্চয় এটি এক সম্মানিত কুরআন।"),
    (12, 2, "We sent it down as an Arabic Quran, so that you may understand.", "আমি এটি আরবি কুরআন হিসেবে নাযিল করেছি, যাতে তোমরা বুঝতে পারো।"),
    (73, 5, "We will send down to you a weighty word.", "আমি তোমার ওপর এক ভারী বাণী নাযিল করব।"),
    (18, 27, "Recite what has been revealed to you of the Book of your Lord. None can change His words, and you will find no refuge besides Him.", "তোমার প্রতিপালকের কিতাব থেকে তোমার প্রতি যা ওহি করা হয়েছে, তা পাঠ করো। তাঁর বাণী পরিবর্তন করার কেউ নেই, আর তাঁকে ছাড়া তুমি কোনো আশ্রয় পাবে না।"),
    (97, 1, "We sent it down on the Night of Power.", "আমি এটি কদরের রাতে নাযিল করেছি।"),
    (41, 3, "A Book whose ayahs are set out clearly, an Arabic Quran for people who know.", "এমন এক কিতাব, যার আয়াতগুলো বিশদভাবে বর্ণিত, আরবি কুরআন, জ্ঞানী সম্প্রদায়ের জন্য।"),
    (2, 121, "Those to whom We gave the Book, who recite it as it should be recited, believe in it.", "যাদের আমি কিতাব দিয়েছি, তারা তা যথাযথভাবে তিলাওয়াত করে; তারাই এতে ঈমান আনে।"),
]

# (collection, api edition, api hadithnumber, sunnah.com number, start phrase, end phrase, English, Bangla)
HADITHS = [
    ("bukhari", "ara-bukhari", 5027, "5027", "خيركم من تعلم", "وعلمه", "The best of you are those who learn the Quran and teach it.", "তোমাদের মধ্যে সেই ব্যক্তিই সর্বোত্তম, যে কুরআন শেখে এবং শেখায়।"),
    ("bukhari", "ara-bukhari", 4937, "4937", "مثل الذي يقرا القران وهو حافظ له", "اجران", "The one who recites the Quran and has memorised it is with the noble, obedient angels; the one who keeps at it and finds it hard has two rewards.", "যে কুরআন মুখস্থ রেখে পাঠ করে, সে সম্মানিত ও অনুগত ফেরেশতাদের সঙ্গে থাকবে; আর যে বারবার চর্চা করে এবং তার জন্য তা কঠিন, তার জন্য দুটি প্রতিদান।"),
    ("bukhari", "ara-bukhari", 5031, "5031", "انما مثل صاحب القران", "ذهبت", "The one who keeps the Quran is like the owner of tied camels: if he looks after them he keeps them, and if he lets them go they are gone.", "কুরআনের সঙ্গী সেই ব্যক্তির মতো, যার বাঁধা উট আছে: সে যত্ন নিলে উট ধরে রাখে, ছেড়ে দিলে চলে যায়।"),
    ("bukhari", "ara-bukhari", 5033, "5033", "تعاهدوا القران", "في عقلها", "Keep up the Quran. By Him in whose hand is my soul, it slips away faster than camels from their ropes.", "কুরআনের যত্ন নাও। সেই সত্তার কসম, যাঁর হাতে আমার প্রাণ, এটি দড়ি থেকে উটের পালানোর চেয়েও দ্রুত হারিয়ে যায়।"),
    ("bukhari", "ara-bukhari", 5032, "5032", "استذكروا القران", "من النعم", "Keep revising the Quran, for it escapes from men's hearts faster than camels from their ropes.", "কুরআন বারবার মনে করো, কারণ এটি মানুষের অন্তর থেকে দড়ি ছাড়ানো উটের চেয়েও দ্রুত পালিয়ে যায়।"),
    ("bukhari", "ara-bukhari", 5025, "5025", "لا حسد", "اناء الليل", "No envy is allowed except for two: a man to whom Allah gave the Book and who stands with it through the night.", "দুই ব্যক্তি ছাড়া কারও প্রতি ঈর্ষা জায়েয নয়: এক, যাকে আল্লাহ কিতাব দিয়েছেন এবং সে রাতে তা নিয়ে দাঁড়ায়।"),
    ("bukhari", "ara-bukhari", 5013, "5013", "والذي نفسي بيده انها لتعدل ثلث القران", "ثلث القران", "By Him in whose hand is my soul, it equals a third of the Quran.", "সেই সত্তার কসম, যাঁর হাতে আমার প্রাণ, এটি কুরআনের এক-তৃতীয়াংশের সমান।"),
    ("bukhari", "ara-bukhari", 5009, "5009", "من قرا بالايتين", "كفتاه", "Whoever recites the last two ayahs of Surah al-Baqarah at night, they will be enough for him.", "যে ব্যক্তি রাতে সূরা বাকারার শেষ দুই আয়াত পাঠ করে, তা তার জন্য যথেষ্ট হবে।"),
    ("bukhari", "ara-bukhari", 5020, "5020", "مثل الذي يقرا القران", "وريحها طيب", "The one who recites the Quran is like a citron: its taste is good and its smell is good.", "যে কুরআন পাঠ করে, সে কমলালেবুর মতো: এর স্বাদ ভালো এবং ঘ্রাণও ভালো।"),
    ("bukhari", "ara-bukhari", 5060, "5060", "اقرءوا القران ما ائتلفت", "فقوموا", "Recite the Quran as long as your hearts agree on it; when you differ, rise and leave it.", "যতক্ষণ তোমাদের অন্তর একমত থাকে, কুরআন পাঠ করো; মতভেদ হলে উঠে যাও।"),
    ("bukhari", "ara-bukhari", 4992, "4992", "ان هذا القران انزل", "ما تيسر منه", "This Quran was revealed in seven ways, so recite what is easy of it.", "এই কুরআন সাত রীতিতে নাযিল হয়েছে, তাই তার মধ্য থেকে যা সহজ তা পাঠ করো।"),
    ("bukhari", "ara-bukhari", 5049, "5049", "اقرا علي القران", "علي القران", "Recite the Quran to me.", "আমাকে কুরআন পড়ে শোনাও।"),
    ("bukhari", "ara-bukhari", 3461, "3461", "بلغوا عني ولو اية", "ولو اية", "Convey from me, even if it is one ayah.", "আমার পক্ষ থেকে পৌঁছে দাও, যদি তা একটি আয়াতও হয়।"),
    ("bukhari", "ara-bukhari", 6, "6", "وكان يلقاه في كل ليلة", "القران", "Gabriel would meet him every night of Ramadan and go over the Quran with him.", "জিবরীল রমযানের প্রতি রাতে তাঁর সঙ্গে সাক্ষাৎ করতেন এবং তাঁর সঙ্গে কুরআন পুনরাবৃত্তি করতেন।"),
    ("muslim", "ara-muslim", 1874, "804a", "اقرءوا القران فانه ياتي", "لاصحابه", "Recite the Quran, for on the Day of Resurrection it will come as an intercessor for its companions.", "কুরআন পাঠ করো, কারণ কিয়ামতের দিন এটি তার সঙ্গীদের জন্য সুপারিশকারী হয়ে আসবে।"),
    ("muslim", "ara-muslim", 1862, "798a", "الماهر بالقران", "اجران", "The one who is skilled in the Quran is with the noble angels; the one who recites with difficulty has two rewards.", "কুরআনে দক্ষ ব্যক্তি সম্মানিত ফেরেশতাদের সঙ্গে থাকবে; যে কষ্ট করে পাঠ করে, তার জন্য দুটি প্রতিদান।"),
    ("muslim", "ara-muslim", 1897, "817a", "ان الله يرفع بهذا الكتاب", "اخرين", "Allah raises some people by this Book and lowers others by it.", "আল্লাহ এই কিতাবের মাধ্যমে কিছু জাতিকে উঁচু করেন এবং অন্যদের নিচু করেন।"),
    ("muslim", "ara-muslim", 6853, "2699a", "وما اجتمع قوم في بيت", "فيمن عنده", "When people gather in one of the houses of Allah, reciting the Book of Allah and studying it together, tranquillity descends on them, mercy covers them, angels surround them, and Allah mentions them to those with Him.", "যখন কিছু মানুষ আল্লাহর কোনো ঘরে জড়ো হয়ে আল্লাহর কিতাব তিলাওয়াত করে এবং নিজেদের মধ্যে তা পড়ে, তখন তাদের ওপর প্রশান্তি নামে, রহমত তাদের ঢেকে নেয়, ফেরেশতারা তাদের ঘিরে রাখে এবং আল্লাহ নিজের কাছের লোকদের কাছে তাদের কথা বলেন।"),
    ("muslim", "ara-muslim", 1891, "814a", "الم تر ايات انزلت", "مثلهن قط", "Have you not seen the ayahs sent down tonight, the like of which has never been seen?", "তুমি কি দেখোনি আজ রাতে কেমন আয়াত নাযিল হয়েছে, যার মতো আর কখনো দেখা যায়নি?"),
    ("muslim", "ara-muslim", 1886, "811a", "ايعجز احدكم ان يقرا", "ثلث القران", "Can any of you not recite a third of the Quran in a night?", "তোমাদের কেউ কি এক রাতে কুরআনের এক-তৃতীয়াংশ পড়তে অক্ষম?"),
    ("muslim", "ara-muslim", 1878, "807a", "الايتان من اخر", "كفتاه", "The last two ayahs of Surah al-Baqarah: whoever recites them at night, they will be enough for him.", "সূরা বাকারার শেষ দুই আয়াত: যে রাতে তা পাঠ করে, তা তার জন্য যথেষ্ট হবে।"),
    ("muslim", "ara-muslim", 1824, "780", "لا تجعلوا بيوتكم مقابر", "سورة البقرة", "Do not make your houses graves. Satan runs from the house in which Surah al-Baqarah is recited.", "তোমাদের ঘরকে কবর বানিও না। যে ঘরে সূরা বাকারা পাঠ করা হয়, শয়তান সেখান থেকে পালায়।"),
    ("muslim", "ara-muslim", 1885, "810", "يا ابا المنذر", "معك اعظم", "Abu al-Mundhir, do you know which ayah of the Book of Allah that you know is the greatest?", "আবুল মুনযির, তুমি কি জানো আল্লাহর কিতাবের তোমার জানা কোন আয়াত সবচেয়ে মহান?"),
    ("muslim", "ara-muslim", 1860, "797a", "مثل المؤمن الذي يقرا القران", "طعمها طيب", "The believer who recites the Quran is like a citron: its taste is good and its smell is good.", "যে মুমিন কুরআন পাঠ করে, সে কমলালেবুর মতো: এর স্বাদ ভালো এবং ঘ্রাণও ভালো।"),
    ("muslim", "ara-muslim", 1839, "789a", "انما مثل صاحب القران", "ذهبت", "The one who keeps the Quran is like the owner of tied camels: if he keeps watch over them he holds them, and if he lets them go they leave.", "কুরআনের সঙ্গী বাঁধা উটের মালিকের মতো: সে নজর রাখলে ধরে রাখে, ছেড়ে দিলে চলে যায়।"),
    ("muslim", "ara-muslim", 6777, "2667a", "اقرءوا القران ما ائتلفت", "فقوموا", "Recite the Quran as long as your hearts agree on it; when you differ, rise and leave it.", "যতক্ষণ তোমাদের অন্তর একমত থাকে, কুরআন পাঠ করো; মতভেদ হলে উঠে যাও।"),
]


def strip_marks(text):
    """Returns the text without vowel signs and with alif and ya forms merged, plus an index map."""
    kept, index_map = [], []
    for index, char in enumerate(text):
        if DIACRITICS.match(char):
            continue
        kept.append(LETTER_FORMS.get(char, char))
        index_map.append(index)
    return "".join(kept), index_map


def cut_saying(report, start_phrase, end_phrase):
    plain, index_map = strip_marks(report)
    start_key, _ = strip_marks(start_phrase)
    end_key, _ = strip_marks(end_phrase)
    start = plain.find(start_key)
    if start < 0:
        raise SystemExit(f"start phrase not found: {start_phrase}")
    end = plain.find(end_key, start)
    if end < 0:
        raise SystemExit(f"end phrase not found: {end_phrase}")
    last_plain_index = end + len(end_key) - 1
    last = index_map[last_plain_index]
    # Keep the vowel signs that belong to the last letter.
    while last + 1 < len(report) and DIACRITICS.match(report[last + 1]):
        last += 1
    return report[index_map[start]:last + 1].strip()


def load_editions():
    editions = {}
    for edition in ("ara-bukhari", "ara-muslim"):
        with urllib.request.urlopen(API.format(edition=edition), timeout=120) as response:
            editions[edition] = {item["hadithnumber"]: item["text"] for item in json.load(response)["hadiths"]}
    return editions


def main():
    data_directory = data_paths.published()
    database = sqlite3.connect(f"file:{data_directory / 'quran' / 'quran.sqlite'}?mode=ro", uri=True)
    editions = load_editions()

    snippets = []
    for surah, ayah, english, bangla in AYAHS:
        row = database.execute("SELECT text_uthmani FROM ayah WHERE surah = ? AND ayah = ?", (surah, ayah)).fetchone()
        if row is None:
            raise SystemExit(f"no ayah {surah}:{ayah}")
        snippets.append(
            {
                "id": f"quran-{surah}-{ayah}",
                "arabic": snippet_text.quran_snippet_text(row[0], ayah),
                "source": {"type": "quran", "surah": surah, "ayah": ayah},
                "text_en": english,
                "text_bn": bangla,
                "url": None,
                "grade": None,
            }
        )
    for collection, edition, api_number, number, start, end, english, bangla in HADITHS:
        report = editions[edition].get(api_number)
        if report is None:
            raise SystemExit(f"{edition} has no hadith {api_number}")
        snippets.append(
            {
                "id": f"{collection}-{number}",
                "arabic": cut_saying(report, start, end),
                "source": {"type": "hadith", "collection": collection, "number": number},
                "text_en": english,
                "text_bn": bangla,
                "url": f"https://sunnah.com/{collection}:{number}",
                "grade": "sahih",
            }
        )

    document = {
        "note": "Draft renderings by the Awn project; a reviewer must check them. Hadith are graded sahih as they stand in Sahih al-Bukhari and Sahih Muslim.",
        "snippets": snippets,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(snippets)} snippets to {OUTPUT}")


if __name__ == "__main__":
    main()
