package autocomplete.datasource

/**
 * We use an entire case class for failures for a few reasons.
 * Firstly, better type safety.
 * Secondly, with AnyVal, it's going to be only the error causing string,
 * rather than having to create something like "The error string was: " + string
 * which would mean interning more strings. And that's bad
 * @param search the string in question
 */

case class SearchFailure(search: String) extends AnyVal
