"""The list of translation packs, with what is known about each one's copyright.

build_translations.py reads TRANSLATION_PACKS to know what to build, build_manifests.py
reads it for the manifest entries, and `python3 tools/pack_catalogue.py` prints the
Markdown table that docs/packs.md holds.

Meaning of the two judgement columns:
  terms_known  "yes"     a public statement of terms exists and applies to the text Awn ships
               "unclear" a statement exists but it is not certain that it covers this text
               "no"      no public statement granting redistribution was found
  publishable  "yes"         public domain or a permissive licence; may go in a release
               "conditional" only under the stated conditions (Tanzil: non-commercial use,
                             no change to the text, a link back to tanzil.net/trans)
               "no"          do not publish until the maintainer has permission
The maintainer decides what is published; these values record what the builder found.

tanzil_id names the Tanzil file (https://tanzil.net/trans/<tanzil_id>) that tools/check_tanzil_provenance.py
compares with the Quran.com text. Tanzil's terms ("for non-commercial purposes only; otherwise obtain
permission from the translator or the publisher", link back when more than three are used) apply to
a pack only when the comparison shows the Quran.com text is the Tanzil text.
"""

TANZIL_TERMS = (
    "Tanzil terms (https://tanzil.net/trans/): non-commercial use only, otherwise permission from the "
    "translator or publisher; a link back to the Tanzil page when more than three translations are used."
)

# The Tanzil terms bind a pack only when its text equals the Tanzil text; the nine packs marked "yes" with a
# tanzil_id have at least 96 percent of their ayahs identical to Tanzil's (check_tanzil_provenance.py).
# Fields: id, resource_id, language (BCP 47), language_name, name, author, rights holder or publisher,
# tanzil_id, terms_known, publishable, note
# The first four are the packs of the first release (data-v1); their files are not rebuilt differently.
TRANSLATION_PACKS = [
    # English
    dict(id="en_saheeh", resource_id=20, language="en", language_name="English", name="Saheeh International",
         author="Saheeh International", rights_holder="Dar Abul-Qasim (AbulQasim Publishing House), Jeddah",
         tanzil_id="en.sahih", terms_known="unclear", publishable="conditional",
         note="Copyrighted by the publisher; Tanzil lists it under its non-commercial terms."),
    dict(id="en_abdul_haleem", resource_id=85, language="en", language_name="English", name="M.A.S. Abdel Haleem",
         author="M.A.S. Abdel Haleem", rights_holder="Oxford University Press",
         tanzil_id=None, terms_known="no", publishable="no",
         note="Copyrighted by Oxford University Press; no grant to redistribute found."),
    dict(id="en_pickthall", resource_id=19, language="en", language_name="English", name="Pickthall",
         author="Mohammed Marmaduke William Pickthall (died 1936)", rights_holder="public domain",
         tanzil_id="en.pickthall", terms_known="yes", publishable="yes",
         note="Quran.com text equals Tanzil's (6231 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back. The translator died in 1936, so the 1930 text is out of copyright in life-plus-70 countries."),
    dict(id="en_yusuf_ali", resource_id=22, language="en", language_name="English", name="Yusuf Ali",
         author="Abdullah Yusuf Ali (died 1953)", rights_holder="original 1934-37 text public domain; revised Amana editions copyrighted",
         tanzil_id="en.yusufali", terms_known="yes", publishable="conditional",
         note="Quran.com text equals Tanzil's (6219 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back. Which edition Quran.com holds is not stated; the revised edition (Amana Corporation, 1989) is copyrighted."),
    dict(id="en_usmani", resource_id=84, language="en", language_name="English", name="Taqi Usmani",
         author="Mufti Taqi Usmani", rights_holder="Maktaba Ma'ariful Quran (The Noble Quran / Meanings of the Noble Quran)",
         tanzil_id=None, terms_known="no", publishable="no", note="Copyrighted; no grant found."),
    dict(id="en_hilali_khan", resource_id=203, language="en", language_name="English", name="Hilali and Khan",
         author="Muhammad Taqi-ud-Din al-Hilali and Muhammad Muhsin Khan", rights_holder="Darussalam Publishers (Noble Quran)",
         tanzil_id="en.hilali", terms_known="unclear", publishable="conditional",
         note="Copyrighted; Tanzil lists it under its non-commercial terms."),
    dict(id="en_bridges", resource_id=149, language="en", language_name="English", name="Bridges (Fadel Soliman)",
         author="Fadel Soliman", rights_holder="Bridges Foundation",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    # Bangla
    dict(id="bn_zakaria", resource_id=213, language="bn", language_name="Bangla", name="Dr. Abu Bakr Muhammad Zakaria",
         author="Dr. Abu Bakr Muhammad Zakaria", rights_holder="King Fahd Quran Printing Complex edition (rights holder not stated by the source)",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="bn_taisirul", resource_id=161, language="bn", language_name="Bangla", name="Taisirul Quran",
         author="Taisirul Quran", rights_holder="Tawheed Publication",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="bn_rawai_al_bayan", resource_id=162, language="bn", language_name="Bangla", name="Rawai Al-Bayan",
         author="Rawai Al-Bayan", rights_holder="Bayaan Foundation",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="bn_mujibur_rahman", resource_id=163, language="bn", language_name="Bangla", name="Sheikh Mujibur Rahman",
         author="Sheikh Mujibur Rahman", rights_holder="Darussalam Publication",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    # Urdu
    dict(id="ur_junagarhi", resource_id=54, language="ur", language_name="Urdu", name="Muhammad Junagarhi",
         author="Maulana Muhammad Junagarhi (died 1941)", rights_holder="translator died 1941",
         tanzil_id="ur.junagarhi", terms_known="yes", publishable="conditional",
         note="Quran.com text equals Tanzil's (6145 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back."),
    dict(id="ur_jalandhari", resource_id=234, language="ur", language_name="Urdu", name="Fateh Muhammad Jalandhari",
         author="Fateh Muhammad Jalandhari (died 1981)", rights_holder="not stated",
         tanzil_id="ur.jalandhry", terms_known="yes", publishable="conditional",
         note="Quran.com text equals Tanzil's (6232 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back."),
    dict(id="ur_bayan_ul_quran", resource_id=158, language="ur", language_name="Urdu", name="Bayan-ul-Quran (Israr Ahmad)",
         author="Dr. Israr Ahmad", rights_holder="Markazi Anjuman Khuddam-ul-Quran",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="ur_wahiduddin_khan", resource_id=819, language="ur", language_name="Urdu", name="Wahiduddin Khan",
         author="Maulana Wahiduddin Khan", rights_holder="Goodword Books / Centre for Peace and Spirituality",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="ur_roman_maududi", resource_id=831, language="ur-Latn", language_name="Roman Urdu", name="Maududi (Roman Urdu)",
         author="Abul Ala Maududi", rights_holder="Islamic Publications, Lahore",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    # Hindi
    dict(id="hi_azizul_haque", resource_id=122, language="hi", language_name="Hindi", name="Maulana Azizul Haque al-Umari",
         author="Maulana Azizul Haque al-Umari", rights_holder="not stated",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    # Indonesian
    dict(id="id_kemenag", resource_id=33, language="id", language_name="Indonesian", name="Indonesian Ministry of Religious Affairs",
         author="Indonesian Islamic Affairs Ministry (Kemenag)", rights_holder="Government of Indonesia, Ministry of Religious Affairs",
         tanzil_id="id.indonesian", terms_known="unclear", publishable="conditional",
         note="Tanzil lists it under its non-commercial terms."),
    dict(id="id_king_fahad", resource_id=134, language="id", language_name="Indonesian", name="King Fahad Quran Complex",
         author="King Fahad Quran Complex", rights_holder="King Fahd Quran Printing Complex",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="id_sabiq", resource_id=141, language="id", language_name="Indonesian", name="The Sabiq Company",
         author="The Sabiq Company", rights_holder="The Sabiq Company",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    # Turkish
    dict(id="tr_diyanet", resource_id=77, language="tr", language_name="Turkish", name="Diyanet Isleri",
         author="Diyanet Isleri (Presidency of Religious Affairs)", rights_holder="Diyanet Isleri Baskanligi",
         tanzil_id="tr.diyanet", terms_known="unclear", publishable="conditional",
         note="Tanzil lists it under its non-commercial terms."),
    dict(id="tr_elmalili", resource_id=52, language="tr", language_name="Turkish", name="Elmalili Hamdi Yazir",
         author="Elmalili Hamdi Yazir (died 1942)", rights_holder="translator died 1942",
         tanzil_id="tr.yazir", terms_known="yes", publishable="conditional",
         note="Quran.com text equals Tanzil's (6236 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back."),
    dict(id="tr_shaban_britch", resource_id=112, language="tr", language_name="Turkish", name="Shaban Britch",
         author="Shaban Britch", rights_holder="not stated",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="tr_muslim_shahin", resource_id=124, language="tr", language_name="Turkish", name="Muslim Shahin",
         author="Muslim Shahin", rights_holder="not stated",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    # French
    dict(id="fr_hamidullah", resource_id=31, language="fr", language_name="French", name="Muhammad Hamidullah",
         author="Muhammad Hamidullah (died 2002)", rights_holder="not stated",
         tanzil_id="fr.hamidullah", terms_known="unclear", publishable="conditional",
         note="Tanzil lists it under its non-commercial terms."),
    dict(id="fr_montada", resource_id=136, language="fr", language_name="French", name="Montada Islamic Foundation",
         author="Montada Islamic Foundation", rights_holder="Montada Islamic Foundation",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="fr_rashid_maash", resource_id=779, language="fr", language_name="French", name="Rashid Maash",
         author="Rashid Maash", rights_holder="not stated",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    # Spanish
    dict(id="es_isa_garcia", resource_id=83, language="es", language_name="Spanish", name="Sheikh Isa Garcia",
         author="Muhammad Isa Garcia", rights_holder="not stated",
         tanzil_id="es.garcia", terms_known="unclear", publishable="conditional",
         note="Tanzil lists it under its non-commercial terms."),
    dict(id="es_montada", resource_id=140, language="es", language_name="Spanish", name="Montada Islamic Foundation",
         author="Montada Islamic Foundation", rights_holder="Montada Islamic Foundation",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    dict(id="es_noor", resource_id=199, language="es", language_name="Spanish", name="Noor International Center",
         author="Noor International Center", rights_holder="Noor International Center",
         tanzil_id=None, terms_known="no", publishable="no", note="No licence text found."),
    # Malay
    dict(id="ms_basmeih", resource_id=39, language="ms", language_name="Malay", name="Abdullah Muhammad Basmeih",
         author="Abdullah Muhammad Basmeih", rights_holder="Department of Islamic Development Malaysia (JAKIM)",
         tanzil_id="ms.basmeih", terms_known="yes", publishable="conditional",
         note="Quran.com text equals Tanzil's (6231 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back."),
    # Russian
    dict(id="ru_kuliev", resource_id=45, language="ru", language_name="Russian", name="Elmir Kuliev",
         author="Elmir Kuliev", rights_holder="not stated",
         tanzil_id="ru.kuliev", terms_known="yes", publishable="conditional",
         note="Quran.com text equals Tanzil's (6232 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back."),
    dict(id="ru_muntahab", resource_id=78, language="ru", language_name="Russian", name="Ministry of Awqaf, Egypt (Al-Muntakhab)",
         author="Ministry of Awqaf, Egypt", rights_holder="Ministry of Awqaf, Egypt",
         tanzil_id="ru.muntahab", terms_known="yes", publishable="conditional",
         note="Quran.com text equals Tanzil's (6236 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back."),
    dict(id="ru_abu_adel", resource_id=79, language="ru", language_name="Russian", name="Abu Adel",
         author="Abu Adel", rights_holder="not stated",
         tanzil_id="ru.abuadel", terms_known="yes", publishable="conditional",
         note="Quran.com text equals Tanzil's (6236 of 6,236 ayahs identical, tools/check_tanzil_provenance.py), so the Tanzil terms apply: non-commercial use, link back."),
]

# The transliteration of the first release; build_translations.py builds it with the original code path.
TRANSLITERATION_PACKS = [
    dict(id="en_transliteration", resource_id=57, language="en", language_name="English (Latin letters)",
         name="English transliteration", author="Transliteration", rights_holder="not stated",
         tanzil_id="en.transliteration", terms_known="unclear", publishable="conditional",
         note="The only transliteration in the Quran.com resource list; Tanzil lists it under its non-commercial terms."),
]

TRANSLATIONS_AND_TRANSLITERATIONS = TRANSLITERATION_PACKS + TRANSLATION_PACKS

# Word-by-word packs: Quran.com word translations, selected with the language query parameter.
WORD_PACKS = [
    dict(id="wbw_en", language="en", language_name="English", api_language_name="english"),
    dict(id="wbw_bn", language="bn", language_name="Bangla", api_language_name="bengali"),
    dict(id="wbw_ur", language="ur", language_name="Urdu", api_language_name="urdu"),
    dict(id="wbw_hi", language="hi", language_name="Hindi", api_language_name="hindi"),
    dict(id="wbw_id", language="id", language_name="Indonesian", api_language_name="indonesian"),
    dict(id="wbw_tr", language="tr", language_name="Turkish", api_language_name="turkish"),
]

# Packs that exist in the first release and must keep their bytes; the v2 build checks them.
FIRST_RELEASE_PACK_IDS = ["en_saheeh", "en_abdul_haleem", "en_transliteration", "bn_zakaria", "wbw_en", "wbw_bn"]


def markdown_table(sizes_by_id):
    lines = [
        "| id | Quran.com resource | language | translator | rights holder | terms known | publishable | bytes |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for pack in TRANSLATIONS_AND_TRANSLITERATIONS:
        size = sizes_by_id.get(pack["id"], "")
        lines.append(
            f"| {pack['id']} | {pack['resource_id']} | {pack['language_name']} | {pack['author']} | "
            f"{pack['rights_holder']} | {pack['terms_known']} | {pack['publishable']} | {size} |")
    return "\n".join(lines)


if __name__ == "__main__":
    print(markdown_table({}))
