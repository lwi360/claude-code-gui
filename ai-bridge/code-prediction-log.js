const writeErr = console.error.bind(console);
console.log = (...args) => writeErr(...args);
console.info = (...args) => writeErr(...args);
console.debug = (...args) => writeErr(...args);
