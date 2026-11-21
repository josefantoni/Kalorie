const DIACRITIC_FOLD_MAP = {
  'á': 'a', 'Á': 'A',
  'č': 'c', 'Č': 'C',
  'ď': 'd', 'Ď': 'D',
  'é': 'e', 'É': 'E',
  'ě': 'e', 'Ě': 'E',
  'í': 'i', 'Í': 'I',
  'ň': 'n', 'Ň': 'N',
  'ó': 'o', 'Ó': 'O',
  'ř': 'r', 'Ř': 'R',
  'š': 's', 'Š': 'S',
  'ť': 't', 'Ť': 'T',
  'ú': 'u', 'Ú': 'U',
  'ů': 'u', 'Ů': 'U',
  'ý': 'y', 'Ý': 'Y',
  'ž': 'z', 'Ž': 'Z',
  'ä': 'a', 'Ä': 'A',
  'ô': 'o', 'Ô': 'O',
  'ĺ': 'l', 'Ĺ': 'L',
  'ľ': 'l', 'Ľ': 'L',
  'ŕ': 'r', 'Ŕ': 'R',
};

function foldDiacritics(input) {
  return input.split('').map((c) => DIACRITIC_FOLD_MAP[c] ?? c).join('');
}

module.exports = { foldDiacritics };
