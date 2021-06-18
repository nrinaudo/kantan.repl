The following triggers a warning

```scala repl
def get[A](oa: Option[A]): A = oa match {
  case Some(a) => a
}
```
