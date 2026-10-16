"""The diffs DiffParityTest replays through both platforms: the Demo Mode diffs
plus cases for every language, indentation, word emphasis, Unicode and chunking."""
import json
import os
import re
import textwrap

here = os.path.dirname(os.path.abspath(__file__))
demo_src = open(os.path.join(
    here, "../../../core/demo/src/main/kotlin/cx/lpm/mobile/core/demo/DemoGitDiffs.kt"
)).read()


def demo(name, path=None):
    """A diff from Demo Mode's seed, as the demo serves it."""
    match = re.search(
        r'(?:fun ' + name + r'\(path: String\): String = |val ' + name + r' = )"""\n(.*?)\n""".trimIndent\(\)',
        demo_src, re.S)
    text = textwrap.dedent(match.group(1)).replace("${'$'}", "$")
    return text.replace("$path", path) if path else text


def d(text):
    return textwrap.dedent(text).lstrip("\n")


cases = [
    {"name": "demo-checkout-form", "ext": "tsx", "diff": demo("demoCheckoutFormDiff")},
    {"name": "demo-checkout-page", "ext": "tsx", "diff": demo("demoCheckoutPageDiff")},
    {"name": "demo-payments", "ext": "ts", "diff": demo("demoPaymentsDiff")},
    {"name": "demo-readme", "ext": "md", "diff": demo("demoReadmeDiff")},
    {"name": "demo-added", "ext": "ts", "diff": demo("genericAddedDiff", "src/new.ts")},
    {"name": "demo-modified", "ext": "ts", "diff": demo("genericModifiedDiff", "src/app.ts")},
    {"name": "empty", "ext": "ts", "diff": ""},
    {"name": "swift", "ext": "swift", "diff": d("""
        diff --git a/App.swift b/App.swift
        index 1111111..2222222 100644
        --- a/App.swift
        +++ b/App.swift
        @@ -10,8 +10,11 @@ struct ContentView: View {
             /* A block comment
                that spans lines */
        -    let count = 0x1F + 1_000
        +    let count = 0x2F + 2_000 // more
             var name = "say \\"hi\\""
        +    guard let value = try? await load() else { return nil }

        +    /* opened
        +
        +       still open */ let after = 3.14
             func body() -> some View { Text("x") }
        \\ No newline at end of file
        """)},
    {"name": "rust", "ext": "rs", "diff": d("""
        @@ -1,4 +1,4 @@
         use std::io;
        -fn main() -> Result<(), Box<dyn Error>> {
        +pub fn main() -> Result<(), Box<dyn Error>> {
             let mut v: Vec<u8> = Vec::new(); // bytes
             println!("{}", 42u32);
        """)},
    {"name": "python", "ext": "py", "diff": d("""
        @@ -3,6 +3,7 @@ class Thing:
             def go(self, x):
        -        return None if x is None else x * 2
        +        return x and x * 2  # doubled
        +        yield 'single' "double"
             async def run():
                 await asyncio.sleep(0.5)
        """)},
    {"name": "go", "ext": "go", "diff": d("""
        @@ -1,5 +1,5 @@
         package main
        -import "fmt"
        +import `fmt`
         func main() {
        -	var x int = 10
        +	x := map[string]int{"a": 1}
         }
        """)},
    {"name": "ruby", "ext": "rb", "diff": d("""
        @@ -1,3 +1,3 @@
        -def hello(name) # greet
        +def hello(name = "world") # greet
           puts "hi #{name}" unless name.nil?
         end
        """)},
    {"name": "shell", "ext": "sh", "diff": d("""
        @@ -1,4 +1,4 @@
         #!/bin/sh
        -if [ -f "$1" ]; then echo 'found'; fi
        +if [ -d "$1" ]; then echo 'dir'; fi
         export PATH=$HOME/bin:$PATH # path
        """)},
    {"name": "json", "ext": "json", "diff": d("""
        @@ -1,5 +1,5 @@
         {
        -  "name": "storefront",
        +  "name": "storefront-web",
           "private": true,
           "version": null, "count": 12.5e3
         }
        """)},
    {"name": "yaml", "ext": "yml", "diff": d("""
        @@ -1,12 +1,13 @@
         # lpm project
         name: storefront
        -services:
        +services: # all of them
           web:
             cmd: npm run dev
             port: 3000
             env: {NODE_ENV: development}
        +    url: http://localhost:3000
           - item
           - key: value
           - - nested: true
           "quoted": no
         ---
         a:b
        """)},
    {"name": "css", "ext": "css", "diff": d("""
        @@ -1,4 +1,4 @@
         /* theme */
        -.btn { color: #fff; margin: 0 4px; }
        +.btn { color: #000; margin: 0 8px; content: "x"; }
         a[href='x'] { }
        """)},
    {"name": "scss", "ext": "scss", "diff": d("""
        @@ -1,3 +1,3 @@
        -$gap: 4px; // small
        +$gap: 8px; // bigger
         .a { /* note */ padding: $gap; }
        """)},
    {"name": "html", "ext": "html", "diff": d("""
        @@ -1,5 +1,6 @@
         <!doctype html>
        -<div class="a" id='b'>text 12</div>
        +<div class="a b" id='b'>text 13</div>
        +<!-- a comment
        + spanning --> <p>after</p>
         <p>end</p>
        """)},
    {"name": "plain-markdown", "ext": "md", "diff": d("""
        @@ -1,3 +1,3 @@
         # Title
        -Some "text" with 12 numbers // not a comment
        +Some "text" with 13 numbers // still not
        """)},
    {"name": "unknown-ext", "ext": "", "diff": d("""
        @@ -1 +1 @@
        -let a = 1
        +let a = 2
        """)},
    {"name": "uppercase-ext", "ext": "TS", "diff": d("""
        @@ -1 +1 @@
        -const a = 1
        +const a = 2
        """)},
    {"name": "indent-trim", "ext": "ts", "diff": d("""
        @@ -20,6 +20,7 @@ function outer() {
                 if (ready) {
        -            start()
        +            start(options)
        +            log("started")

                     return true
                 }
        """)},
    {"name": "indent-tabs", "ext": "go", "diff": d("""
        @@ -5,4 +5,4 @@ func f() {
         		a := 1
        -		b := 2
        +		b := 3
         	
         		return
        """)},
    {"name": "indent-mixed", "ext": "py", "diff": d("""
        @@ -1,4 +1,4 @@
             x = 1
        -	y = 2
        +	y = 3
             z = 4
        """)},
    {"name": "emphasis-pairs", "ext": "ts", "diff": d("""
        @@ -1,10 +1,10 @@
        -const userName = getName(id)
        -const total = price * quantity
        +const userLabel = getName(id)
        +const total = price * qty
         keep()
        -abc
        +xyz
        -removeOnly(1)
        -removeTwo(2)
        +addOnly(1)
         same()
        -foo(bar_baz)
        +foo(bar_qux)
        -
        +x
        """)},
    {"name": "emphasis-whole", "ext": "ts", "diff": d("""
        @@ -1,2 +1,2 @@
        -completely different line
        +another thing entirely here
        -prefix_same_suffix
        +prefix_same_suffix2
        """)},
    {"name": "unicode", "ext": "ts", "diff": d("""
        @@ -1,4 +1,4 @@
        -const greeting = "héllo 👋 wörld"
        +const greeting = "héllo 👋🏽 wörld"
        -const kanji = "東京都"
        +const kanji = "京都府"
         const flag = "🇺🇸" // country
        -const cafe = "café"
        +const café = "café"
        """)},
    {"name": "renamed", "ext": "ts", "diff": d("""
        diff --git a/old.ts b/new.ts
        similarity index 90%
        rename from old.ts
        rename to new.ts
        old mode 100644
        new mode 100755
        index 1..2
        --- a/old.ts
        +++ b/new.ts
        @@ -1,2 +1,2 @@
        -export const a = 1
        +export const a = 2
         export const b = 3
        """)},
    {"name": "deleted-file", "ext": "ts", "diff": d("""
        diff --git a/gone.ts b/gone.ts
        deleted file mode 100644
        --- a/gone.ts
        +++ /dev/null
        @@ -1,2 +0,0 @@
        -export const a = 1
        -export const b = 2
        """)},
    {"name": "big-numbers", "ext": "rs", "diff": d("""
        @@ -998,4 +1002,5 @@ impl Thing {
             fn a() {}
        -    fn b() {}
        +    fn b() -> u8 { 1 }
        +    fn c() {}
             fn d() {}
        @@ -12000,2 +12005,2 @@
        -    x
        +    y
        """)},
    {"name": "no-trailing-newline", "ext": "ts", "diff": "@@ -1,2 +1,2 @@\n-a\n+b\n c"},
    {"name": "malformed-hunk", "ext": "ts", "diff": "@@ broken\n+added\n context\n"},
    {"name": "context-without-space", "ext": "ts", "diff": "@@ -1,3 +1,3 @@\nraw context\n-x\n+y\n"},
    {"name": "crlf", "ext": "ts", "diff": "@@ -1,2 +1,2 @@\r\n-a = 1\r\n+a = 2\r\n"},
    {"name": "crlf-mixed", "ext": "ts",
     "diff": "diff --git a/x b/x\r\n@@ -1,3 +1,3 @@ ctx\r\n keep\n-old\r\n+new\r\n\\ No newline at end of file"},
]

long_old = [f"-    old line {i} " + "x" * (i % 7) for i in range(30)]
long_new = [f"+    new line {i} " + "y" * (i % 11) + (" // tail" if i % 5 == 0 else "") for i in range(30)]
long_ctx = [f"     context {i}" for i in range(20)]
cases.append({"name": "chunked", "ext": "ts",
              "diff": "@@ -1,50 +1,50 @@ chunk\n" + "\n".join(long_ctx + long_old + long_new) + "\n"})

cases += [
    {"name": "hl-swift", "ext": "swift", "lines": [
        "let x = 0b1010 + 0o17 + 1.5e3 // done",
        "/* starts", "", "middle */ var y = \"a\\\\\"", "", "s = \"unterminated", "ident1 2ident _x $y"]},
    {"name": "hl-yaml", "ext": "yaml", "lines": [
        "key: value", "  - name: x # comment", "- plain", "'quoted': 1", "a:b", "---", "key:",
        "  :", "   : x", "url: \"http://x:1\"", "on: yes"]},
    {"name": "hl-html", "ext": "html", "lines": ["<!-- one", "two", "three --> <b>4</b>"]},
    {"name": "hl-plain", "ext": "txt", "lines": ["anything // here", ""]},
    {"name": "hl-numbers", "ext": "js", "lines": [
        "a1 1a 0xFF 1_000.5 x.5 .5 ２３ １", "foo(12)+bar[3]"]},
]

print(json.dumps(cases, indent=1, ensure_ascii=False))
