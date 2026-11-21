function slovakName(slovak, czech, english) {
  const first = [slovak, czech, english].find((value) => typeof value === 'string' && value.trim().length > 0);
  return first ?? '';
}

module.exports = { slovakName };
