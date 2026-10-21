const { foldDiacritics } = require('./diacritics');

const EDGE_PUNCTUATION = '()[]{},;:.!?"\'„“”‚‘’«»';

function searchWords(folded) {
  return folded
    .split(' ')
    .map((word) => {
      let start = 0;
      let end = word.length;
      while (start < end && EDGE_PUNCTUATION.includes(word[start])) start++;
      while (end > start && EDGE_PUNCTUATION.includes(word[end - 1])) end--;
      return word.slice(start, end);
    })
    .filter((w) => w.length > 0);
}

function searchTerms(input) {
  const folded = foldDiacritics(input.toLowerCase());
  const terms = new Set();
  for (const word of searchWords(folded)) {
    for (let length = 1; length <= word.length; length++) {
      terms.add(word.slice(0, length));
    }
  }
  return Array.from(terms);
}

module.exports = { searchTerms };
