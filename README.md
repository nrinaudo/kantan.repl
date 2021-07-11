# kantan.repl

kantan.repl is essentially a bare-bone clone of [tut](https://github.com/tpolecat/tut) that handles Scala 3. It's probably only ever going to be useful to me and my very specific use-case.

If you're looking for a good tool to type-check and run the code in your markdown files, I would suggest you checking out [mdoc](https://github.com/scalameta/mdoc). It does a lot more than kantan.repl even will, and lacks a single feature: it doesn't act as a REPL session.

This is the critical part for me. When writing presentations, I need to be able to write code as in a REPL session. I need to be able to redefine the same function many times, with incremental improvements. The fact that `mdoc` doesn't cater to that very specific use-case is the only reason kantan.repl exists.

## Documentation

The main purpose of kantan.repl is to go through a markdown file, run any code block flagged as `scala repl` through a REPL and replace its content with the output.

Here are the supported modifiers:
* `print`: print the output as a comment.
* `invisible`: hides the corresponding block (this is sometimes useful to add uninteresting but necessary import statements, for example).
* `fail`: expect the block to fail (either at compile or at runtime).
* `silent`: run the code, but do not display its output.

There are a subset of the ones supported by `tut` and `mdoc`, because they're the only ones I need. More might be added as needed.

## Running kantan.repl

The most straightforward way of using kantan.repl is as an SBT task, which you can achieve by adding the following to your plugins:

```scala
addSbtPlugin("com.nrinaudo" % "kantan.repl-sbt" % "X.Y.Z")
```

It'll add the following SBT keys:
* `mdReplSourceDirectory`: source directory for markdown files you need processed (defaults to `./src/main/mdrepl`).
* `mdReplTargetDirectory`: target directory for processed markdown files (defaults to `./target/scala-3.0.0/mdrepl`).
* `mdReplNameFilter`: filter for files to process (defaults to `"*.markdown" || "*.md" || "*.htm" || "*.html"`).
* `mdRepl`: task that processes all files found in `mdReplSourceDirectory`.

The way I use kantan.repl is mostly in conjunction with [sbt-site](https://github.com/sbt/sbt-site), which is why there's another plugin to integrate both of them:

```sbt
addSbtPlugin("com.nrinaudo" % "kantan.repl-sbt-site" % "X.Y.Z")
```

This takes care of hooking things together so that running `makeSite` will automatically process markdown files and copy the output to the site's root directory (this can be configured by changing `MdRepl / siteSubdirName`).
