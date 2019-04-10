# C++ tokenizer for Vietnamese

This project provides tokenizer library for Vietnamese language and 2 command line tools for tokenization and some simple Vietnamese-specific operations with text (i.e. remove diacritics).

### Installing

Building from source and installing into sandbox (or into the system):

```
$ mkdir build && cd build
$ cmake -DCMAKE_INSTALL_PREFIX=/path/to/your/prefix ..
$ make install
```

To include java bindings:

```
$ mkdir build && cd build
$ cmake -DBUILD_JAVA=1 -DCMAKE_INSTALL_PREFIX=/path/to/your/prefix ..
$ make install
```

Building debian package can be done with debhelper tools:

```
$ dpkg-buildpackage <options> # from source tree root
```

## Using the tools

Both tools will show their usage with `--help` option. Both tools can accept either command line arguments or stdin as an input (if both provided, command line arguments are preferred). If stdin is used, each line is considered as one separate argument. The output format is TAB-separated tokens of the original phrase (note that Vietnamese tokens can have whitespaces inside). There's a few examples of usage below.

Tokenize command line argument:

```
$ tokenizer "Từng bước để trở thành một lập trình viên giỏi"
từng	bước	để	trở thành	một	lập trình	viên	giỏi
```

Note that it may take one or two seconds for tokenizer to load due to one comparably big dictionary used to tokenize "sticky phrases" (when people write words without spacing). You can disable it by using `-n` option and the tokenizer will be up in no time. The default behaviour about "sticky phrases" is to only try to split them within urls or domains. With `-n` you can disable it completely and with `-u` you can force using it for the whole text. Compare:

```
$ tokenizer "toisongohanoi, tôi đăng ký trên thegioididong.vn"
toisongohanoi	tôi	đăng ký	trên	the gioi	di dong	vn

$ tokenizer -n "toisongohanoi, tôi đăng ký trên thegioididong.vn"
toisongohanoi	tôi	đăng ký	trên	thegioididong	vn

$ tokenizer -u "toisongohanoi, tôi đăng ký trên thegioididong.vn"
toi	song	o	ha noi	tôi	đăng ký	trên	the gioi	di dong	vn
```

To avoid reloading dictionaries for every phrase, you can pass phrases from stdin. Here's an example (note that the first line of output is empty - that means empty result for "/" input line):

```
$ echo -ne "/\nanh yêu em\nbún chả ở nhà hàng Quán Ăn Ngon ko ngon\n" | tokenizer

anh	yêu	em
bún	chả	ở	nhà hàng	quán ăn	ngon	ko	ngon
```

The usage of `vn_lang_tool` is pretty similar, you can see full list of options for both tools by using:

```
$ tokenizer --help
$ vn_lang_tool --help
```

## Using the library

Use the code of both tools as an example of usage for a library, they are pretty straightforward and easy to understand:

```
tests/test.cpp # for tokenizer tool
tests/vn_lang_tool_test.cpp # for vn_lang_tool
```

## Using Java bindings

A java interface is provided to be used in java projects. Internally it utilizes JNI and the Unsafe API to connect Java and C++. To run the test class from source tree, use the following command:

```
$ LD_LIBRARY_PATH=build java -cp build/coccoc-tokenizer.jar com.coccoc.Tokenizer "một câu văn tiếng Việt"
```

Normally `LD_LIBRARY_PATH` should point to a directory with `libcoccoc_tokenizer_jni.so` binary. If you have already installed deb package or `make install`-ed everything into your system, `LD_LIBRARY_PATH` is not needed as the binary will be taken from your system (`/usr/lib` or similar).

## Other languages

Bindings for other languages are not yet implemented but it will be nice if someone can help to write them.

## Benchmark

The library provides high speed tokenization which is a requirement for performance critical applications.

The benchmark is done on a typical laptop with Intel Core i5-5200U processor:
- Dataset: **1.203.165** Vietnamese Wikipedia articles ([Link](https://drive.google.com/file/d/1Amh8Tp3rM0kdThJ0Idd88FlGRmuwaK6o/view?usp=sharing))
- Output: **106.042.935** tokens out of **630.252.179** characters
- Processing time: **41** seconds
- Speed: **15M** characters / second, or **2.5M** tokens / second
