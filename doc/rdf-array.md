# Jena on the `rdf-array` branch

This branch is Apache Jena 6.3.0 with the sequence term of
[rdf-array](https://github.com/rdf-array/rdf-array) and the empty slots of its `rdf-undef` branch:

- **The term.** `( 1 2 3 )` is one term, `Node_Seq`, and `undef` marks an empty slot (`Node_Undef`).
- **Parsing.** Turtle, TriG, N-Triples and N-Quads read sequences.
- **SPARQL.** It has SPARQL-array:
  - sequence patterns with gaps `...` and named gaps `...?rest`;
  - `UNNEST`;
  - the functions `isSEQ`, `SEQLEN`, `SEQ`, `s[i]`, `s[m...n]` and `IN ?seq`;
  - `seq` and `undef` in the JSON and XML result formats.

It is jena-array's `rdf-undef` branch, ported onto Jena 6.3.0.

## Where it comes from

| | |
|---|---|
| Base | the `jena-6.3.0` tag, under the version `6.3.0-array-SNAPSHOT` so that installing it never replaces stock 6.3.0 |
| Ported | [jena-array](https://github.com/rdf-array/jena-array) `rdf-undef` @ `d0f7501780` (2026-11-03): its 15 commits over fhircat/jena `all-rdf` @ `744357a559`, Jena 5.0.0-SNAPSHOT |
| How | the 15 commits squashed over their branch point and 3-way merged onto 6.3.0 with rename detection; the generated parsers regenerated from `Grammar/main.jj` with JavaCC 7.0.13 and GNU `cpp` (`Grammar/grammar`) |
| Modules | jena-core and jena-arq; TDB1 and TDB2 adapted to the new matcher |
| Left out | jena-shex: its rdf-array changes sit on all-rdf's ShEx rework, which upstream Jena does not have. jena-shacl: not needed by this branch's first user (BeakGraph). The `jena-rdfarray` demo module |

## Where it differs from jena-array

Each of these follows Jena 6.x, where 5.0 and 6.x differ.

- **Names.** 6.x renamed RDF-star to RDF 1.2: `isTripleTerm`, `createTripleTerm`, `getLiteralBaseDirection`,
  and the SSE tag `tripleterm`. A triple term is written `<<( s p o )>>`, also inside a sequence.
- **`UNNEST` beside `UNFOLD`.** 6.x has SPARQL-CDT's `UNFOLD`. `OpUnnest` and `ElementUnnest` stand beside
  `OpUnfold` and `ElementUnfold` in every visitor, transform and walker.
- **N-Triples spelling.** `NodeFmtLib.strNT` writes N-Triples terms in full, inside a sequence too.
  jena-array's 5.0 used the Turtle formatter there.
- **Templates.** `CONSTRUCT` substitutes into a template sequence node by node, through 6.x's
  `TemplateLib.subst`:
  - its blank nodes are renewed for each solution, as anywhere in a template;
  - an unbound variable inside it writes a slot.
- **Validity.** `NodeUtils.isValidAsRDF` accepts a sequence as an object (elements: RDF terms or slots).
  Without it, 6.x's `CONSTRUCT` dropped every triple holding a sequence.
- **Lists.** 6.x's `G` (`listMembers`, `listLength`, `getOneOrList`) and `GraphList` treat a sequence term
  as the list it is.
- **Results.** 6.x reads JSON results with the streaming reader only, which carries the sequence support.
  The results comparison and isomorphism are 6.x's `BNodeIso` and `IsoAlgTuple`.
- **Solver.** `SolverRX3` and `SolverRX4` are jena-array's, which match sequence patterns with
  backtracking over gap lengths. `SolverLib.makeAbortable` keeps 6.x's cancel flag. TDB1's and TDB2's
  `SolverRX` take `matchQuad`'s several results.
- **Tests.**
  - jena-array's tests run on JUnit 5: `TestLangSequences`, `TestSequenceQueries`,
    `TestArraySyntaxExamples` and `TestUndefSlots`.
  - So does the rdf-tests-contrib copy in `jena-arq/testing/rdf-array`, through `Scripts_RdfArrayContrib`
    and `Scripts_RdfArrayContribSPARQL`. Its SPARQL tests parse as ARQ syntax, decided by the manifest's
    location.

## Status

- **The rdf-array suites pass on 6.3.0:**
  - `TestUndefSlots` 43, `TestSequenceQueries` 39, `TestArraySyntaxExamples` 14, `TestLangSequences` 14;
  - rdf-tests-contrib: RDF syntaxes 54, SPARQL 204. These are all the entries of the manifests copied
    here.
- **jena-core: all 10,179 tests pass.**
- **jena-arq: 12,322 tests, 89 failing, in five classes and two kinds** (2026-11-24). Neither kind is in the
  sequence or SPARQL machinery; both are rdf-array's known open work.
  - **Tests built on `rdf:first`/`rdf:rest` ladders:** `TestGList`, 6.x's list utility, 31 of 49; and
    `TestList` 1 of 46. Their Turtle `( … )` data is now one sequence term. The rdf-array convention is to
    flag such tests and give them a sequence counterpart, never to drop them silently; not done yet.
  - **Writers with no spelling for a sequence:** JSON-LD (`@list` is not mapped), RDF/XML, RDF Thrift
    and RDF Protobuf. Jena's writer tests use data that now holds sequences: `TestRiotWriterGraph` 45 of
    646, `TestRiotWriterDataset` 8 of 176, `TestJsonLDReadWrite` 4 of 8. rdf-undef.md §5 lists these
    syntaxes as needing an encoding decision each.

## Building

```
mvn install -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Drat.skip=true \
    -pl apache-jena-libs,jena-fuseki2/jena-fuseki-main -am
```

On Windows, Fuseki UI's Node and yarn steps fail on files the virus scanner holds open. Add
`-Dskip.installnodecorepack=true -Dskip.corepack=true -Pui-skip-tests`; jena-fuseki-main needs the UI module
only for its tests. Regenerating the grammar needs `javacc` and `jjdoc` 7.0.x and GNU `cpp` on the path, then
`cd jena-arq/Grammar && ./grammar`.
