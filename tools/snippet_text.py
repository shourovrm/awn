"""The Arabic of a Quran snippet as the Learn tab shows it, shared by build_snippets.py and
check_snippets.py.

The Quran database writes each stop sign (U+06D6 to U+06DC) as its own word after a space. Drawn
like that, the sign has no letter under it, and a browser or phone puts it on a dotted circle; the
Mushaf prints it above the last letter of the word before. The snippet also ends with the ayah
number in Arabic-Indic digits, which the Hafs font draws inside the ayah-end ornament (the font
needs no U+06DD before them; with it, the ornament is drawn twice).
"""

import re

STOP_SIGNS = "".join(chr(code) for code in range(0x06D6, 0x06DD))
SPACE_BEFORE_STOP_SIGN = re.compile(f"\\s+([{STOP_SIGNS}])")
ARABIC_INDIC_ZERO = 0x0660


def quran_snippet_text(ayah_text, ayah_number):
    """Returns [ayah_text] with its stop signs joined to the word before and its number at the end."""
    joined = SPACE_BEFORE_STOP_SIGN.sub(r"\1", ayah_text)
    return f"{joined} {arabic_indic(ayah_number)}"


def arabic_indic(number):
    """Writes [number] in Arabic-Indic digits: 114 becomes ١١٤."""
    return "".join(chr(ARABIC_INDIC_ZERO + int(digit)) for digit in str(number))
