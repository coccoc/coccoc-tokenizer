# C++ tokenizer for Vietnamese

This project provides tokenizer library for Vietnamese language and 2 command line tools for tokenization and some simple Vietnamese-specific operations with text (i.e. remove diacritics). It is used in Cốc Cốc Search and Ads systems and the main goal in its development was to reach high performance while keeping the quality reasonable for search ranking needs.

### Installing

Building from source and installing into sandbox (or into the system):

```
$ mkdir build && cd build
$ cmake ..
# make install
```

To include java bindings:

```
$ mkdir build && cd build
$ cmake -DBUILD_JAVA=1 ..
# make install
```

To include python bindings - install [cython](https://pypi.org/project/Cython/) package and compile wrapper code (only Python3 is supported):

```
$ mkdir build && cd build
$ cmake -DBUILD_PYTHON=1 ..
# make install
```

Building debian package can be done with debhelper tools:

```
$ dpkg-buildpackage <options> # from source tree root
```

If you want to build and install everything into your sandbox, you can use something like this (it will build everything and install into ~/.local, which is considered as a standard sandbox PREFIX by many applications and frameworks):
```
$ mdkir build && cd build
$ cmake -DBUILD_JAVA=1 -DBUILD_PYTHON=1 -DCMAKE_INSTALL_PREFIX=~/.local ..
$ make install
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
utils/tokenizer.cpp # for tokenizer tool
utils/vn_lang_tool.cpp # for vn_lang_tool
```

Here's a short code snippet from there:

```cpp
// initialize tokenizer, exit in case of failure
if (0 > Tokenizer::instance().initialize(opts.dict_path, !opts.no_sticky))
{
	exit(EXIT_FAILURE);
}

// tokenize given text, two additional options are:
//   - bool for_transforming - this option is Cốc Cốc specific kept for backwards compatibility
//   - int tokenize_options - TOKENIZE_NORMAL, TOKENIZE_HOST or TOKENIZE_URL,
//     just use Tokenizer::TOKENIZE_NORMAL if unsure
std::vector< FullToken > res = Tokenizer::instance().segment(text, false, opts.tokenize_option);

for (FullToken t : res)
{
	// do something with tokens
}
```

Note that you can call `segment()` function of the same Tokenizer instance multiple times and in parallel from multiple threads.

Here's a short explanation of fields in FullToken structure:

```cpp
struct Token {
	// position of the start of normalized token (in chars)
	int32_t normalized_start;
	// position of the end of normalized token (in chars)
	int32_t normalized_end;
	// position of the start of token in original text (in bytes)
	int32_t original_start;
	// position of the end of token in original text (in bytes)
	int32_t original_end;
	// token type (WORD, NUMBER, SPACE or PUNCT)
	int32_t type;
	// token segmentation type (this field is Cốc Cốc specific and kept for backwards compatibility)
	int32_t seg_type;
}

struct FullToken : Token {
	// normalized token text
	std::string text;
}

```

## Using Java bindings

A java interface is provided to be used in java projects. Internally it utilizes JNI and the Unsafe API to connect Java and C++. You can find an example of its usage in `Tokenizer` class's main function:

```
java/src/java/Tokenizer.java
```

To run this test class from source tree, use the following command:

```
$ LD_LIBRARY_PATH=build java -cp build/coccoc-tokenizer.jar com.coccoc.Tokenizer "một câu văn tiếng Việt"
```

Normally `LD_LIBRARY_PATH` should point to a directory with `libcoccoc_tokenizer_jni.so` binary. If you have already installed deb package or `make install`-ed everything into your system, `LD_LIBRARY_PATH` is not needed as the binary will be taken from your system (`/usr/lib` or similar).

## Using Python bindings

```python
from CocCocTokenizer import PyTokenizer

# load_nontone_data is True by default
T = PyTokenizer(load_nontone_data=True)

# tokenize_option:
# 	0: TOKENIZE_NORMAL (default)
#	1: TOKENIZE_HOST
#	2: TOKENIZE_URL
print(T.word_tokenize("xin chào, tôi là người Việt Nam", tokenize_option=0))

# output: ['xin', 'chào', ',', 'tôi', 'là', 'người', 'Việt_Nam']
```

## Other languages

Bindings for other languages are not yet implemented but it will be nice if someone can help to write them.

## Benchmark

The library provides high speed tokenization which is a requirement for performance critical applications.

The benchmark is done on a typical laptop with Intel Core i5-5200U processor:
- Dataset: **1.203.165** Vietnamese Wikipedia articles ([Link](https://drive.google.com/file/d/1Amh8Tp3rM0kdThJ0Idd88FlGRmuwaK6o/view?usp=sharing))
- Output: **106.042.935** tokens out of **630.252.179** characters
- Processing time: **41** seconds
- Speed: **15M** characters / second, or **2.5M** tokens / second
- RAM consumption is around **300Mb**

## Quality Comparison

The `tokenizer` tool has a special output format which is similar to other existing tools for tokenization of Vietnamese texts - it preserves all the original text and just marks multi-syllable tokens with underscores instead of spaces. Compare:

```
$ tokenizer 'Lan hỏi: "điều kiện gì?".'
lan     hỏi     điều kiện       gì

$ tokenizer -f original 'Lan hỏi: "điều kiện gì?".'
Lan hỏi: "điều_kiện gì?".
```

Using the [following testset](https://github.com/UniversalDependencies/UD_Vietnamese-VTB) for comparison with [underthesea](https://github.com/undertheseanlp/underthesea) and [RDRsegmenter](https://github.com/datquocnguyen/RDRsegmenter), we get significantly lower result, but for most of the cases the observed differences are not important for search ranking quality. Below you can find few examples of such differences. Please, be aware of them when using this library.

```
original         : Em út theo anh cả vào miền Nam.
coccoc-tokenizer : Em_út theo anh_cả vào miền_Nam.
underthesea      : Em_út theo anh cả vào miền Nam.
RDRsegmenter     : Em_út theo anh_cả vào miền Nam.

original         : kết quả cuộc thi phóng sự - ký sự 2004 của báo Tuổi Trẻ.
coccoc-tokenizer : kết_quả cuộc_thi phóng_sự - ký_sự 2004 của báo Tuổi_Trẻ.
underthesea      : kết_quả cuộc thi phóng_sự - ký_sự 2004 của báo Tuổi_Trẻ.
RDRsegmenter     : kết_quả cuộc thi phóng_sự - ký_sự 2004 của báo Tuổi_Trẻ.

original         : cô bé lớn lên dưới mái lều tranh rách nát, trong một gia đình có bốn thế hệ phải xách bị gậy đi ăn xin.
coccoc-tokenizer : cô_bé lớn lên dưới mái lều tranh rách_nát, trong một gia_đình có bốn thế_hệ phải xách bị gậy đi ăn_xin.
underthesea      : cô bé lớn lên dưới mái lều tranh rách_nát, trong một gia_đình có bốn thế_hệ phải xách bị_gậy đi ăn_xin.
RDRsegmenter     : cô bé lớn lên dưới mái lều tranh rách_nát, trong một gia_đình có bốn thế_hệ phải xách bị_gậy đi ăn_xin.
```

We also don't apply any named entity recognition mechanisms within the tokenizer and have few rare cases where we fail to solve ambiguity correctly. We thus didn't want to provide exact quality comparison results as probably the goals and potential use cases of this library and of those similar ones mentioned above are different and thus precise comparison doesn't make much sense.

## Future Plans

We'd love to introduce bindings for Python and maybe other languages later and we'd be happy if somebody can help us doing that. We are also thinking about adding POS tagger and more complex linguistic features later.

If you find any issues or have any suggestions regarding further upgrades, please, report them here or write us through github.
