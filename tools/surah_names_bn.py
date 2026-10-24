"""Reads tools/data/surah_names_bn.tsv: the spoken Bangla name and the Bangla meaning of each surah.

The file is the checked-in copy of Bangla Wikipedia's surah list (CC BY-SA 4.0); its header comment
names the revision and the changes made. Two tools use it: build_quran_text.py when it builds
quran.sqlite from scratch, and apply_surah_names_bn.py when it updates a built file.
"""

from pathlib import Path

NAMES_FILE = Path(__file__).resolve().parent / "data" / "surah_names_bn.tsv"
SURAH_COUNT = 114
SOURCE_NOTE = (
    "Bangla surah names: Bangla Wikipedia, list of surahs (CC BY-SA 4.0), "
    "tools/data/surah_names_bn.tsv"
)


def read_surah_names(path=NAMES_FILE):
    """Map surah number to (spoken name, meaning). The meaning is None when the source gives none."""
    names = {}
    for line_number, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip() or line.startswith("#"):
            continue
        columns = line.split("\t")
        if columns[0] == "number":
            continue
        if len(columns) != 3:
            raise ValueError(f"{path}:{line_number}: expected 3 tab-separated columns, got {len(columns)}")
        number = int(columns[0])
        spoken_name, meaning = columns[1].strip(), columns[2].strip()
        if not spoken_name:
            raise ValueError(f"{path}:{line_number}: surah {number} has no spoken name")
        if number in names:
            raise ValueError(f"{path}:{line_number}: surah {number} appears twice")
        names[number] = (spoken_name, meaning or None)
    if sorted(names) != list(range(1, SURAH_COUNT + 1)):
        raise ValueError(f"{path}: expected surahs 1 to {SURAH_COUNT}, found {len(names)} rows")
    return names
