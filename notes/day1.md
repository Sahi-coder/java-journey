# Java Journey, Day 1: Setup, Compiler, Bytecode, and the JVM

Notes covering JDK installation, compiling and running a first program by hand, the internal phases of the Java compiler, inspecting bytecode with `javap`, and watching the JVM load classes and JIT-compile hot code.

## Contents

1. [Install the JDK](#install-the-jdk)
2. [Write and compile your first program by hand](#write-and-compile-your-first-program-by-hand)
3. [How `javac` works internally](#how-javac-works-internally)
4. [Look inside your `.class` file](#look-inside-your-class-file)
5. [Watch the JVM load classes and the JIT at work](#watch-the-jvm-load-classes-and-the-jit-at-work)

---

## Install the JDK

**Version:** JDK 21. It is an LTS (long-term support) release, which is what most companies use.

**Where to get it:** [adoptium.net](https://adoptium.net), download "Temurin 21 (LTS)" for your OS. It is free and widely trusted. (Oracle's JDK build also works; it behaves the same for everything here.)

### Install

| OS | Instructions |
|----|--------------|
| Windows | Download the `.msi` and run it. On the "Custom Setup" screen, enable **Set JAVA_HOME variable** and **Add to PATH**. This is the step people most often miss. |
| Mac | Download the `.pkg` and run it. |
| Linux (Ubuntu/Debian) | `sudo apt install openjdk-21-jdk` |

### Verify

Open a **new** terminal (an old one will not see the install), then run:

```bash
java -version
javac -version
```

Expected: `openjdk version "21.0.x"` for the first and `javac 21.0.x` for the second.

**Why check both?** `java` is the JVM (runs programs) and `javac` is the compiler. If `java` works but `javac` is "not recognized", you either installed only a JRE or PATH is not set properly.

---

## Write and compile your first program by hand

### 2a. Make a work folder

```bat
cd %USERPROFILE%
mkdir java-journey
cd java-journey
mkdir day1
cd day1
```

### 2b. Create the source file

```bat
notepad Hello.java
```

Click **Yes** when asked to create the file, then paste:

```java
public class Hello {
    public static void main(String[] args) {
        int a = 5;
        int b = 10;
        int sum = a + b;
        System.out.println("Sum: " + sum);
    }
}
```

Save with `Ctrl+S` and close Notepad.

> **Check the file name.** Notepad sometimes saves as `Hello.java.txt`. Run `dir` and confirm you see `Hello.java`.

### 2c. Compile

```bat
javac Hello.java
```

No output means success. Run `dir` again and you should see a new `Hello.class`. That is the bytecode produced by `javac`.

### 2d. Run

```bat
java Hello
```

Type `Hello`, not `Hello.class` and not `Hello.java`. You hand the JVM a class name and it finds the file itself.

Output:

```
Sum: 15
```

### What just happened

```
Hello.java  --javac-->  Hello.class  --java-->  JVM runs it  -->  "Sum: 15"
```

The full flow, done by hand with no IDE hiding any steps.

---

## How `javac` Works Internally

`javac` is a pipeline. Each phase takes the output of the previous one and refines it. Running example:

```java
int sum = a + b;
```

### Phase 1: Frontend (understanding your code)

#### 1. Lexical analysis (scanning)

The scanner reads the file as a raw character stream and chops it into **tokens**, the smallest meaningful pieces. It cares only about splitting correctly, not about meaning.

| Token | Type |
|-------|------|
| `int` | keyword |
| `sum` | identifier |
| `=` | operator |
| `a` | identifier |
| `+` | operator |
| `b` | identifier |
| `;` | separator |

Whitespace and comments are discarded. Errors caught here: illegal characters (e.g. `int sum = a # b;`).

#### 2. Syntax analysis (parsing)

The parser checks the tokens against Java's grammar (the Java Language Specification).

- `int sum = a + b ;` is valid.
- `int = sum a + ;` has the right pieces in the wrong order and gives errors like `';' expected` or `illegal start of expression`.

If valid, the parser builds an **Abstract Syntax Tree (AST)**:

```
Variable declaration: sum (type int)
        |
   Assignment (=)
        |
   Addition (+)
     /     \
    a       b
```

The tree captures structure and operator precedence. In `a + b * c`, `b * c` sits deeper in the tree so it is evaluated first.

#### 3. Semantic analysis

The code is grammatical, but does it make sense?

- **Symbol resolution:** does `a` exist, and which declaration does it refer to? (`cannot find symbol`)
- **Type checking:** is `a + b` an `int`, and can it be stored in `int sum`? (`incompatible types: String cannot be converted to int`)
- **Type inference:** for `var x = 10;`, the compiler works out `x` is an `int`.
- **Method resolution:** for `print(5)`, which overload matches?

Most everyday compile errors come from this phase.

### Phase 2: Backend (transforming and generating)

#### 4. Flow analysis

The compiler traces every path through the code:

```java
int x;
System.out.println(x);   // error: variable x might not have been initialized
```

```java
int getValue() {
    if (flag) return 5;
}   // error: missing return statement
```

It also checks for unreachable code (statements after a `return`) and that `final` variables are assigned only once. It also works out variable scope boundaries.

#### 5. Desugaring

The JVM does not understand Java's convenience shortcuts ("syntactic sugar"), so the compiler rewrites them into simpler constructs.

| You write | Compiler rewrites to |
|-----------|----------------------|
| `for (String s : list)` | a loop using `Iterator`, `hasNext()`, `next()` |
| `Integer x = 5;` | `Integer x = Integer.valueOf(5);` (autoboxing) |
| No constructor in class | adds default constructor `Foo() { super(); }` |
| Lambdas | `invokedynamic` + a generated synthetic method |
| Generics `List<String>` | `List` (type erasure: types are removed) |

> **Correction on string concatenation:** older material says `"a" + "b"` becomes `StringBuilder.append()` calls. That was true up to Java 8. Since Java 9, `javac` emits an `invokedynamic` instruction and the JVM chooses the best concatenation strategy at runtime. On JDK 21, expect `invokedynamic` in your `javap` output, not `StringBuilder`.

#### 6. Code generation

The tree is converted into stack-based bytecode. For `int sum = a + b;`:

```
iload_1      // push a
iload_2      // push b
iadd         // add them
istore_3     // store in sum
```

For each method the compiler also computes:

- **max stack:** the deepest the operand stack ever gets
- **max locals:** how many local variable slots are needed

This lets the JVM allocate each method's stack frame at exactly the right size.

### Phase 3: Writing the `.class` file

The bytecode is packaged into a binary file with a layout defined by the JVM Specification.

| Section | What it holds |
|---------|---------------|
| Magic number `0xCAFEBABE` | First 4 bytes; marks a valid class file |
| Version | Which Java version compiled it (JDK 21 = major version 65). An older JVM refuses newer versions |
| Constant pool | Table of every literal, class name, method name, and type descriptor. Bytecode refers to entries by index instead of embedding data |
| Access flags | `public`, `final`, `abstract`, etc. |
| This class / super class | Which class this is and what it extends |
| Fields and methods | Each method has its own bytecode array |
| Attributes | Extra metadata: the **line number table** (bytecode offset to source line, which gives stack traces their line numbers) and the **local variable table** (variable names, if compiled with `-g`) |

#### Why the constant pool matters

When bytecode says `invokevirtual #13`, it means "call the method described in constant pool entry 13." That entry points to other entries holding the class name, method name, and signature. The JVM resolves these symbolic references to real memory addresses **lazily**, on first use. This is what makes dynamic class loading possible.

### The whole pipeline

```
Hello.java
   |
   v  Scanner       -> tokens
   v  Parser        -> AST
   v  Semantic      -> names and types resolved
   v  Flow analysis -> initialization, returns, reachability checked
   v  Desugaring    -> foreach, boxing, etc. simplified
   v  Codegen       -> bytecode instructions
   v
Hello.class
```

---

## Look inside your `.class` file

From the `day1` folder:

```bat
cd %USERPROFILE%\java-journey\day1
dir
```

You should see `Hello.java` and `Hello.class`. If `Hello.class` is missing, run `javac Hello.java` first.

### Check the magic number

Windows has no `xxd`, but PowerShell can show raw bytes:

```bat
powershell -command "Format-Hex Hello.class | Select-Object -First 3"
```

The first 4 bytes are:

```
CA FE BA BE
```

That is the magic number. The next 4 bytes are `00 00 00 41`. The last two (`00 41`) are the **major version**: `0x41` = 65 in decimal, which is Java 21.

### Read the bytecode

```bat
javap -c Hello
```

Output (yours may differ slightly):

```
Compiled from "Hello.java"
public class Hello {
  public Hello();
    Code:
       0: aload_0
       1: invokespecial #1    // Method java/lang/Object."<init>":()V
       4: return

  public static void main(java.lang.String[]);
    Code:
       0: iconst_5
       1: istore_1
       2: bipush        10
       4: istore_2
       5: iload_1
       6: iload_2
       7: iadd
       8: istore_3
       9: getstatic     #7    // Field java/lang/System.out:Ljava/io/PrintStream;
      12: iload_3
      13: invokedynamic #13,  0  // InvokeDynamic #0:makeConcatWithConstants:(I)Ljava/lang/String;
      18: invokevirtual #17   // Method java/io/PrintStream.println:(Ljava/lang/String;)V
      21: return
}
```

Two things to notice:

- **A constructor you never wrote.** `public Hello()` was added by the compiler during desugaring. It just calls `Object`'s constructor.
- **`invokedynamic` for string concatenation**, not `StringBuilder` (Java 9+ behavior).

### Match bytecode to source

| Source | Bytecode | Meaning |
|--------|----------|---------|
| `int a = 5;` | `iconst_5`, `istore_1` | push 5, store in slot 1 |
| `int b = 10;` | `bipush 10`, `istore_2` | push 10, store in slot 2 |
| `int sum = a + b;` | `iload_1`, `iload_2`, `iadd`, `istore_3` | push a, push b, add, store in slot 3 |
| `System.out.println(...)` | `getstatic`, `iload_3`, `invokedynamic`, `invokevirtual` | get `System.out`, build the string, call `println` |

**Why does `a` use slot 1 and not slot 0?** In `main`, slot 0 holds `args`.

**Why `iconst_5` for 5 but `bipush 10` for 10?** `iconst_0` to `iconst_5` are one-byte shortcuts for tiny numbers. Larger values need an operand byte, so `bipush` is used.

### Full details: constant pool, stack, locals

```bat
javap -c -v Hello
```

The output is long. Look for:

- **Top section:** `major version: 65`, matching the bytes seen above.
- **Constant pool:** a numbered list (`#1`, `#2`, ...). Find the entry for `"Sum: "` or the recipe string used by the concatenation.
- **Under `main`:** a line like `stack=2, locals=4, args_size=1`.
  - `stack=2`: the operand stack never holds more than 2 values (when adding `a` and `b`).
  - `locals=4`: four slots (`args`, `a`, `b`, `sum`).

### Line number table

Compile with debug info and view the tables:

```bat
javac -g Hello.java
javap -l Hello
```

- `LineNumberTable` maps bytecode offsets to source lines.
- `LocalVariableTable` lists `args`, `a`, `b`, `sum` with their slots.

This is how a stack trace knows "error at line 5."

---

## Watch the JVM load classes and the JIT at work

The earlier sections focused on the compiler. This one looks at what the JVM does when a program runs: class loading, then the JIT compiler.

### Class loading

Run the program with a verbose flag:

```bat
java -verbose:class Hello
```

That prints a lot. To see only the first lines:

```bat
powershell -command "java -verbose:class Hello | Select-Object -First 15"
```

Lines look like:

```
[0.010s][info][class,load] java.lang.Object source: shared objects file
[0.010s][info][class,load] java.io.Serializable source: shared objects file
[0.011s][info][class,load] java.lang.String source: shared objects file
```

What this shows:

- The JVM loads hundreds of its own classes (`Object`, `String`, `System`, ...) before your code starts.
- `source: shared objects file` means they came from the **CDS archive**, a pre-built snapshot that speeds up startup.

To find your own class:

```bat
powershell -command "java -verbose:class Hello | Select-String 'Hello'"
```

```
[0.045s][info][class,load] Hello source: file:/C:/Users/<you>/java-journey/day1/
```

Differences from JDK classes:

- Its source is your folder, not the shared archive.
- It loads late, after the core classes it depends on.

This is **lazy loading**: the JVM loads a class only when it is first needed. In the full output, `Sum: 15` appears after `Hello` is loaded, which shows the class is loaded before `main` runs.

### The JIT compiler

A program with a hot loop, `Loop.java`:

```java
public class Loop {
    public static void main(String[] args) {
        long total = 0;
        for (int i = 0; i < 1_000_000_000; i++) {
            total += i;
        }
        System.out.println(total);
    }
}
```

Compile and time it:

```bat
javac Loop.java
powershell -command "Measure-Command { java Loop } | Select-Object TotalMilliseconds"
```

It prints `499999999500000000` and typically finishes in well under a second. That is too fast for a billion iterations in an interpreter, which is the JIT at work.

Watch the compilation:

```bat
java -XX:+PrintCompilation Loop
```

Lines look like:

```
    45   12       3       Loop::main (27 bytes)
    46   13 %     4       Loop::main @ 5 (27 bytes)
```

| Column | Meaning |
|--------|---------|
| `45` | Milliseconds since JVM start |
| `12` | Compilation ID |
| `%` | OSR (On-Stack Replacement): compiled while the loop was still running |
| `3` or `4` | Tier level: 3 = C1 compiler, 4 = C2 compiler (heavily optimized) |
| `Loop::main` | The method being compiled |

What happens, in order:

1. The JVM starts interpreting `main` (slow).
2. The loop counter shows the code is hot.
3. The JVM compiles it with C1 (tier 3): quick, lightly optimized.
4. It keeps profiling, then recompiles with C2 (tier 4): heavily optimized.
5. Because `main` runs once but loops a billion times, it cannot wait for the next call. OSR swaps the running loop into compiled code mid-execution.

### Why the JIT matters

Run with the JIT disabled so the JVM only interprets:

```bat
powershell -command "Measure-Command { java -Xint Loop } | Select-Object TotalMilliseconds"
```

`-Xint` means interpreter only. Expect it to be many times slower, often tens of seconds versus under a second. (Press `Ctrl+C` if it takes too long.)

### The key idea

```
Start:   interpret bytecode (fast startup, slow execution)
   |
Hot code detected (loops, frequently called methods)
   |
C1 compile (quick, light optimization)
   |
Still hot -> C2 compile (slow to compile, fastest code)
```

This is why Java programs **warm up**: slow at first, faster once the JIT has profiled and compiled the hot paths.
<img width="983" height="935" alt="image" src="https://github.com/user-attachments/assets/08416b42-2a2e-4226-a61c-aa46e5a715c3" />
<img width="1636" height="630" alt="image" src="https://github.com/user-attachments/assets/917902ba-f48e-4c25-aa6c-b27dac333af3" />
<img width="863" height="787" alt="image" src="https://github.com/user-attachments/assets/c0649f53-5870-4d56-bb10-89d9d43115d3" />
<img width="1026" height="517" alt="image" src="https://github.com/user-attachments/assets/a539e69c-a343-41bb-ad2b-27d214911db7" />
<img width="1106" height="938" alt="image" src="https://github.com/user-attachments/assets/c9476d44-404a-4a39-aa3a-f9a4f2a86cee" />
<img width="1200" height="727" alt="image" src="https://github.com/user-attachments/assets/c4ba8625-ba1c-4c9b-b6b0-ea420ae17c72" />
<img width="1130" height="683" alt="image" src="https://github.com/user-attachments/assets/8593d2dc-8dd1-493f-bd31-3ba131e16b11" />
# JIT Compilation Notes: `-XX:+PrintCompilation` on `Loop`

Notes from running a simple long-summing loop with JIT compilation logging enabled.

## Run Command

```bash
C:\JAVA\day1> java -XX:+PrintCompilation Loop
```

Final program output:

```
499999999500000000
```

## How to Read the Output

Each line has the form:

```
timestamp(ms)  compile_id  flags  tier  method (bytecode size)
```

### Flags

| Flag | Meaning |
|------|---------|
| `%`  | OSR (on-stack replacement) compilation |
| `n`  | Native method wrapper |
| `!`  | Method has exception handlers |
| `s`  | Synchronized method |

### Tiers

| Tier | Compiler / Mode |
|------|-----------------|
| 0 | Interpreter |
| 1 | C1, no profiling (used for trivial methods) |
| 2 | C1, limited profiling |
| 3 | C1, full profiling |
| 4 | C2 (fully optimizing) |

### Other markers

- `made not entrant`: the compiled version is retired. No new calls enter it.

## What Happens in This Run

### 1. Startup noise (0 to ~55 ms)

Nearly everything before `Loop::main` is JDK bootstrap, not user code:

- `String`, `HashMap`, `ConcurrentHashMap`
- Module system setup (`ModuleDescriptor`, `addExports0`, `addReads0`)
- `ImmutableCollections`

Tier-1 entries such as `getKey`, `getValue`, and `descriptor` are trivial getters. They are compiled without profiling because there is nothing worth profiling.

### 2. The hot loop

| Time (ms) | ID | Entry | Meaning |
|-----------|----|-------|---------|
| 54 | 59 | `% 3 Loop::main @ 4` | Tier-3 OSR compile at the loop back-edge (bytecode index 4) |
| 55 | 60 | `3 Loop::main` | Normal tier-3 compile of the whole method |
| 56 | 61 | `% 4 Loop::main @ 4` | C2 OSR compile of the same loop |
| 61 | 59 | `% 3 ... made not entrant` | Tier-3 OSR version replaced by the C2 version |
| 330 | 61 | `% 4 ... made not entrant` | C2 OSR version retired after the loop finished |

This is the classic tiered progression:

```
Interpreter -> C1 (tier 3, profiling) -> C2 (tier 4, optimized)
```

OSR is used because `main` runs only once. The JVM cannot wait for the next call, so it swaps in compiled code while the loop is still running.

### 3. Result check

The output `499999999500000000` is the sum 0 + 1 + ... + 999,999,999:

```
n(n-1)/2  with n = 10^9  =  499,999,999,500,000,000
```

The accumulator must be a `long`. An `int` would overflow.

## Key Takeaways

- **Compile IDs are assigned at queue time, not completion time.** IDs can appear out of order (for example, ID 12 shows up before IDs 3 and 1).
- **Replacement is normal.** `Object::<init>` goes tier 3 -> tier 4 (ID 12), then the old tier-3 version (ID 3) is `made not entrant`.
- **Approximate runtime.** The gap between ~61 ms and ~330 ms is the loop running in C2-compiled code, so about 270 ms for 10^9 iterations.
- **Loops in `main` rely on OSR.** Moving the loop body into a separate method that is called repeatedly allows a regular (non-OSR) compile and makes benchmarking more reliable.

## Going Further

- `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` shows inlining decisions C2 made.
- [JMH](https://github.com/openjdk/jmh) gives trustworthy micro-benchmark numbers (warmup, forking, dead-code protection).
- `-XX:+PrintAssembly` (requires hsdis) shows the generated machine code, including unrolling and vectorization of the loop.
<img width="755" height="102" alt="image" src="https://github.com/user-attachments/assets/e25f76a3-2096-4d29-bdf3-73dc489b5152" />

# Git and GitHub Setup Notes

Notes on installing Git, configuring your identity, and fixing GitHub authentication errors on push.

## Contents

1. [Git setup](#git-setup)
2. [Fixing GitHub authentication errors on push](#fixing-github-authentication-errors-on-push)

---

## Git setup

Make sure Git is installed and knows who you are.

### Check if Git is installed

```bat
git --version
```

Expected: something like `git version 2.4x.x.windows.1`.

If you get `'git' is not recognized`, download Git for Windows from [git-scm.com](https://git-scm.com), run the installer, and click Next through the defaults. Then open a **new** terminal and run `git --version` again.

### Tell Git who you are

Every commit records an author, so set your name and email once (use your own, in quotes):

```bat
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

Use the same email as your GitHub account. That is how GitHub links commits to your profile.

Verify it saved:

```bat
git config --global --list
```

Your `user.name` and `user.email` should appear in the output.

### Why this matters

Git stores a commit as: **who, when, message, and a snapshot of your files**. Without a name and email, Git cannot create that record. The author info is stored inside the commit object itself.

---

## Fixing GitHub authentication errors on push

**Symptom:** `git push -u origin main` fails with an authentication error.

**Cause:** Git sent a username and password that GitHub rejected. GitHub does not accept your account password for Git operations; it requires a token. Usually an old or wrong credential is saved on the PC, or the sign-in popup never appeared. Your local repo and commit are fine and nothing is lost.

> The folder you run Git commands from does not matter, as long as it is the one where you ran `git init`. Stay in that folder.

### Fix 1: Clear the saved GitHub credential

1. Press the Windows key, type **Credential Manager**, and open it.
2. Click **Windows Credentials**.
3. Under **Generic Credentials**, find entries named `git:https://github.com` (or containing `github.com`).
4. Click each one, then **Remove**.

Then push again from your repo folder:

```bat
git push -u origin main
```

A browser sign-in window from Git Credential Manager should appear. Choose **Sign in with your browser**, log in to GitHub, and click **Authorize**. The push then continues.

If there is no GitHub entry in Credential Manager, there is nothing to remove. Leave unrelated entries (VS Code, Docker, Microsoft account) alone, since deleting them only signs you out of those apps. Go to Fix 2.

### Fix 2: Use a Personal Access Token

1. On GitHub, click your profile picture, then **Settings**.
2. Scroll to the bottom of the left menu and click **Developer settings**.
3. Click **Personal access tokens**, then **Tokens (classic)**.
4. Click **Generate new token**, then **Generate new token (classic)**.
5. Name it (for example `java-journey`), set an expiry (30 or 90 days), and tick the **repo** checkbox.
6. Click **Generate token** and **copy it immediately**. GitHub shows it only once.

Push again:

```bat
git push -u origin main
```

When prompted:

- **Username:** your GitHub username
- **Password:** paste the token (not your GitHub password). Nothing appears on screen as you paste, which is normal.

Windows should then save the token, so you are not asked again.

### Success check

A successful push prints:

```
branch 'main' set up to track 'origin/main'
```

### Keep the token safe

- Treat it like a password.
- Do not share it, paste it into chat, or put it in a file you commit.
- If you expose it by accident, delete it on the same GitHub page and generate a new one.
<img width="1527" height="857" alt="image" src="https://github.com/user-attachments/assets/9c9523bd-21f4-43ca-8c70-6a0e5211b89b" />
<img width="1545" height="720" alt="image" src="https://github.com/user-attachments/assets/2dbfc56f-456d-4907-a47a-d939e27df081" />
<img width="1315" height="953" alt="image" src="https://github.com/user-attachments/assets/41c4ba10-cef5-4b65-9286-8eba39354b1c" />
<img width="1446" height="647" alt="image" src="https://github.com/user-attachments/assets/9435c42e-054c-4443-9625-441831a4ef73" />
