const { foldDiacritics } = require('./diacritics');

function searchTerms(input) {
  const folded = foldDiacritics(input.toLowerCase());
  const terms = new Set();
  for (const word of folded.split(/ +/).filter((w) => w.length > 0)) {
    for (let length = 1; length <= word.length; length++) {
      terms.add(word.slice(0, length));
    }
  }
  return Array.from(terms);
}

module.exports = { searchTerms };
