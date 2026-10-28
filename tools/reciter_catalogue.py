"""The reciters of the catalogue and the checks that their audio URLs work.

RECITERS lists every reciter Awn can offer: QuranicAudio surah files, EveryAyah ayah files, and, where
Quran.com publishes word segments, a recitation id that tools/build_timings.py turns into a timing pack.

`python3 tools/reciter_catalogue.py` probes the URLs with HEAD requests (no audio is downloaded) and
prints, per reciter, the HTTP status of sample files and the summed size of the 114 surah files.
build_manifests.py reads the probe result from awn-data/reciter_probe.json when it exists.

Sources of the facts:
  QuranicAudio list   https://quranicaudio.com/api/qaris   (id, relative_path)
  EveryAyah folders   https://everyayah.com/data/recitations.js
  Quran.com ids       https://api.quran.com/api/v4/resources/recitations   (those with word segments)
All recitations are Hafs from Asim, the riwaya of the Madani print. The audio sites state no licence;
QuranicAudio and EveryAyah are free-to-listen archives, and nothing here copies audio.
"""

import concurrent.futures
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

SURAH_URL = "https://download.quranicaudio.com/quran/{path}{surah:03d}.mp3"
# The QuranicAudio mirror of EveryAyah lacks some folders that everyayah.com itself serves, so a reciter's
# ayah files use the first base on which all sample files answer; the probe records which one.
AYAH_BASES = ("https://mirrors.quranicaudio.com/everyayah/", "https://everyayah.com/data/")
AYAH_URL = "{base}{folder}/{surah:03d}{ayah:03d}.mp3"
AYAH_COUNTS_SAMPLE_SURAHS = (1, 108, 112)  # 7 + 3 + 4 ayahs: a cheap sample for the ayah-file size estimate
AYAH_COUNTS = {1: 7, 108: 3, 112: 4}

# id, display name, style, QuranicAudio id and path, EveryAyah folder, Quran.com recitation id (word segments) or None.
# "complete" paths of QuranicAudio are the full-Quran sets; partial sets (hidayah) are not listed.
RECITERS = [
    # The five of the first release keep their entries in build_manifests.py; they are repeated here
    # so the probe covers the whole catalogue.
    dict(id="alafasy", name="Mishari Rashid al-Afasy", style="murattal", quranicaudio_id=5,
         quranicaudio_path="mishaari_raashid_al_3afaasee/", everyayah_folder="Alafasy_128kbps", quran_com_recitation_id=7),
    dict(id="husary", name="Mahmoud Khalil Al-Husary (Murattal)", style="murattal", quranicaudio_id=16,
         quranicaudio_path="mahmood_khaleel_al-husaree/", everyayah_folder="Husary_128kbps", quran_com_recitation_id=6),
    dict(id="husary_muallim", name="Mahmoud Khalil Al-Husary (Muallim)", style="muallim", quranicaudio_id=141,
         quranicaudio_path="husary_muallim/", everyayah_folder="Husary_Muallim_128kbps", quran_com_recitation_id=12),
    dict(id="minshawi", name="Mohamed Siddiq al-Minshawi (Murattal)", style="murattal", quranicaudio_id=6,
         quranicaudio_path="muhammad_siddeeq_al-minshaawee/", everyayah_folder="Minshawy_Murattal_128kbps", quran_com_recitation_id=9),
    dict(id="basfar", name="Abdullah Basfar", style="murattal", quranicaudio_id=2,
         quranicaudio_path="abdullaah_basfar/", everyayah_folder="Abdullah_Basfar_192kbps", quran_com_recitation_id=None,
         alternative_everyayah_folders=[{"folder": "Abdullah_Basfar_64kbps", "base_url": "https://everyayah.com/data"}]),
    # Raad Mohammad al-Kurdi (quranicaudio_id 168) was removed: whole-surah audio only, so an ayah would play from the surah start; re-add when ayah start times exist.
    # New in the second catalogue.
    dict(id="sudais", name="Abdur-Rahman as-Sudais", style="murattal", quranicaudio_id=7,
         quranicaudio_path="abdurrahmaan_as-sudays/", everyayah_folder="Abdurrahmaan_As-Sudais_192kbps", quran_com_recitation_id=3),
    dict(id="shuraim", name="Sa'ud ash-Shuraim", style="murattal", quranicaudio_id=4,
         quranicaudio_path="sa3ood_al-shuraym/", everyayah_folder="Saood_ash-Shuraym_128kbps", quran_com_recitation_id=10),
    dict(id="ghamdi", name="Saad al-Ghamdi", style="murattal", quranicaudio_id=13,
         quranicaudio_path="sa3d_al-ghaamidi/complete/", everyayah_folder="Ghamadi_40kbps", quran_com_recitation_id=None),
    dict(id="ajmi", name="Ahmed ibn Ali al-Ajmi", style="murattal", quranicaudio_id=19,
         quranicaudio_path="ahmed_ibn_3ali_al-3ajamy/", everyayah_folder="ahmed_ibn_ali_al_ajamy_128kbps", quran_com_recitation_id=None),
    dict(id="muaiqly", name="Maher al-Muaiqly", style="murattal", quranicaudio_id=159,
         quranicaudio_path="maher_almu3aiqly/year1440/", everyayah_folder="MaherAlMuaiqly128kbps", quran_com_recitation_id=None),
    dict(id="shatri", name="Abu Bakr al-Shatri", style="murattal", quranicaudio_id=3,
         quranicaudio_path="abu_bakr_ash-shaatree/", everyayah_folder="Abu_Bakr_Ash-Shaatree_128kbps", quran_com_recitation_id=4),
    dict(id="abdul_basit_murattal", name="Abdul Basit Abdus-Samad (Murattal)", style="murattal", quranicaudio_id=37,
         quranicaudio_path="abdul_basit_murattal/", everyayah_folder="Abdul_Basit_Murattal_192kbps", quran_com_recitation_id=2),
    dict(id="abdul_basit_mujawwad", name="Abdul Basit Abdus-Samad (Mujawwad)", style="mujawwad", quranicaudio_id=50,
         quranicaudio_path="abdulbaset_mujawwad/", everyayah_folder="Abdul_Basit_Mujawwad_128kbps", quran_com_recitation_id=1),
    dict(id="hudhaify", name="Ali al-Hudhaify", style="murattal", quranicaudio_id=8,
         quranicaudio_path="huthayfi/", everyayah_folder="Hudhaify_128kbps", quran_com_recitation_id=None),
    dict(id="ayyub", name="Muhammad Ayyub", style="murattal", quranicaudio_id=22,
         quranicaudio_path="muhammad_ayyoob/", everyayah_folder="Muhammad_Ayyoub_128kbps", quran_com_recitation_id=None),
    dict(id="rifai", name="Hani ar-Rifai", style="murattal", quranicaudio_id=27,
         quranicaudio_path="rifai/", everyayah_folder="Hani_Rifai_192kbps", quran_com_recitation_id=5),
    dict(id="tablawi", name="Mohamed al-Tablawi", style="murattal", quranicaudio_id=91,
         quranicaudio_path="mohammad_altablawi/", everyayah_folder="Mohammad_al_Tablaway_128kbps", quran_com_recitation_id=11),
    dict(id="minshawi_mujawwad", name="Mohamed Siddiq al-Minshawi (Mujawwad)", style="mujawwad", quranicaudio_id=41,
         quranicaudio_path="minshawi_mujawwad/", everyayah_folder="Minshawy_Mujawwad_192kbps", quran_com_recitation_id=8),
    dict(id="husary_mujawwad", name="Mahmoud Khalil Al-Husary (Mujawwad)", style="mujawwad", quranicaudio_id=164,
         quranicaudio_path="generated/husary_mujawwad/", everyayah_folder="Husary_128kbps_Mujawwad", quran_com_recitation_id=None),
    dict(id="jibreel", name="Muhammad Jibreel", style="murattal", quranicaudio_id=12,
         quranicaudio_path="muhammad_jibreel/complete/", everyayah_folder="Muhammad_Jibreel_128kbps", quran_com_recitation_id=None),
    dict(id="budair", name="Salah al-Budair", style="murattal", quranicaudio_id=43,
         quranicaudio_path="salahbudair/", everyayah_folder="Salah_Al_Budair_128kbps", quran_com_recitation_id=None),
    dict(id="qahtani", name="Khalid al-Qahtani", style="murattal", quranicaudio_id=9,
         quranicaudio_path="khaalid_al-qahtaanee/", everyayah_folder="Khaalid_Abdullaah_al-Qahtaanee_192kbps", quran_com_recitation_id=None),
    dict(id="fares_abbad", name="Fares Abbad", style="murattal", quranicaudio_id=14,
         quranicaudio_path="fares/", everyayah_folder="Fares_Abbad_64kbps", quran_com_recitation_id=None),
    dict(id="qatami", name="Nasser al-Qatami", style="murattal", quranicaudio_id=104,
         quranicaudio_path="nasser_bin_ali_alqatami/", everyayah_folder="Nasser_Alqatami_128kbps", quran_com_recitation_id=None),
    dict(id="dussary", name="Yasser ad-Dussary", style="murattal", quranicaudio_id=97,
         quranicaudio_path="yasser_ad-dussary/", everyayah_folder="Yasser_Ad-Dussary_128kbps", quran_com_recitation_id=None),
    dict(id="juhani", name="Abdullah Awad al-Juhani", style="murattal", quranicaudio_id=1,
         quranicaudio_path="abdullaah_3awwaad_al-juhaynee/", everyayah_folder="Abdullaah_3awwaad_Al-Juhaynee_128kbps", quran_com_recitation_id=None),
    dict(id="mustafa_ismail", name="Mustafa Ismail", style="murattal", quranicaudio_id=88,
         quranicaudio_path="mostafa_ismaeel/", everyayah_folder="Mustafa_Ismail_48kbps", quran_com_recitation_id=None),
    dict(id="matroud", name="Abdullah Matroud", style="murattal", quranicaudio_id=124,
         quranicaudio_path="abdullah_matroud/", everyayah_folder="Abdullah_Matroud_128kbps", quran_com_recitation_id=None),
    dict(id="ali_jaber", name="Ali Jaber", style="murattal", quranicaudio_id=158,
         quranicaudio_path="ali_jaber/", everyayah_folder="Ali_Jaber_64kbps", quran_com_recitation_id=None),
    dict(id="qasim", name="Abdul Muhsin al-Qasim", style="murattal", quranicaudio_id=11,
         quranicaudio_path="abdul_muhsin_alqasim/", everyayah_folder="Muhsin_Al_Qasim_192kbps", quran_com_recitation_id=None),
    dict(id="akhdar", name="Ibrahim al-Akhdar", style="murattal", quranicaudio_id=103,
         quranicaudio_path="ibrahim_al_akhdar/", everyayah_folder="Ibrahim_Akhdar_32kbps", quran_com_recitation_id=None),
    dict(id="sahl_yasin", name="Sahl Yasin", style="murattal", quranicaudio_id=17,
         quranicaudio_path="sahl_yaaseen/", everyayah_folder="Sahl_Yassin_128kbps", quran_com_recitation_id=None),
    dict(id="bukhatir", name="Salah Bukhatir", style="murattal", quranicaudio_id=18,
         quranicaudio_path="salaah_bukhaatir/", everyayah_folder="Salaah_AbdulRahman_Bukhatir_128kbps", quran_com_recitation_id=None),
]

PROBE_FILE = os.path.join(quran_com.DATA_ROOT, "reciter_probe.json")


def head_length(url):
    """Return (status, content_length) for a HEAD request; status is an int or an error string."""
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": quran_com.USER_AGENT})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.status, int(response.headers.get("Content-Length") or 0)
        except urllib.error.HTTPError as error:
            if error.code in (404, 403, 410):
                return error.code, 0
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            if attempt == 2:
                return f"error: {error}", 0
    return "error: retries exhausted", 0


def probe_reciter(reciter):
    surah_urls = [SURAH_URL.format(path=reciter["quranicaudio_path"], surah=s) for s in range(1, 115)]
    surah_results = [head_length(url) for url in surah_urls]
    failed_surahs = [s for s, (status, _) in enumerate(surah_results, 1) if status != 200]
    result = {
        "surah_files_ok": 114 - len(failed_surahs), "failed_surahs": failed_surahs,
        "surah_bytes_total": sum(size for status, size in surah_results if status == 200),
    }
    if reciter["everyayah_folder"]:
        sample = [(s, a) for s in AYAH_COUNTS_SAMPLE_SURAHS for a in range(1, AYAH_COUNTS[s] + 1)]
        surah_sample_bytes = sum(surah_results[s - 1][1] for s in AYAH_COUNTS_SAMPLE_SURAHS)
        result["ayah_sample_count"] = len(sample)
        result["ayah_sample_ok"] = 0
        for base in AYAH_BASES:
            ayah_results = {key: head_length(AYAH_URL.format(base=base, folder=reciter["everyayah_folder"],
                                                             surah=key[0], ayah=key[1])) for key in sample}
            ok_count = sum(1 for status, _ in ayah_results.values() if status == 200)
            if ok_count > result["ayah_sample_ok"]:
                result["ayah_sample_ok"] = ok_count
                result["ayah_base"] = base
            if ok_count == len(sample):
                ayah_sample_bytes = sum(size for status, size in ayah_results.values())
                if surah_sample_bytes and not failed_surahs:
                    ratio = ayah_sample_bytes / surah_sample_bytes
                    result["ayah_to_surah_size_ratio"] = round(ratio, 3)
                    result["ayah_bytes_total_estimate"] = int(result["surah_bytes_total"] * ratio)
                break
    return result


def main():
    wanted = set(sys.argv[1:])
    probes = {}
    if os.path.exists(PROBE_FILE):
        with open(PROBE_FILE, encoding="utf-8") as handle:
            probes = json.load(handle)
    targets = [r for r in RECITERS if not wanted or r["id"] in wanted]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for reciter, result in zip(targets, pool.map(probe_reciter, targets)):
            probes[reciter["id"]] = result
            print(reciter["id"], json.dumps(result), flush=True)
    os.makedirs(os.path.dirname(PROBE_FILE), exist_ok=True)
    with open(PROBE_FILE, "w", encoding="utf-8") as handle:
        json.dump(probes, handle, indent=2, sort_keys=True)


if __name__ == "__main__":
    main()
