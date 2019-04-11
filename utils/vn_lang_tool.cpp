#include <iostream>
#include <vector>
#include <getopt.h>
#include <tokenizer/auxiliary/vn_lang_tool.hpp>
#include <tokenizer/config.h>

struct transform_option
{
	bool keep_case;
	bool to_upper;
	bool keep_unicode_form;
	bool keep_tones;
	const char *dict_path;

	transform_option()
	    : keep_case(false), to_upper(false), keep_unicode_form(false), keep_tones(false), dict_path(DICT_PATH)
	{
	}
};

// clang-format off
static struct option options[] = {
	{ "help"        , no_argument      , NULL,  0  },
	{ "keep-case"   , no_argument      , NULL, 'c' },
	{ "upper-case"  , no_argument      , NULL, 'U' },
	{ "keep-unicode", no_argument      , NULL, 'u' },
	{ "keep-tones"  , no_argument      , NULL, 't' },
	{ "dict-path"   , required_argument, NULL, 'd' },
	{  NULL         , 0                , NULL,  0  }
};
// clang-format on

int print_vn_lang_tool_usage(int argc, char **argv)
{
	fprintf(stderr,
		"Usage:\n"
		"    %s [TEXT]...\n"
		"\n"
		"Options:\n"
		"    -c, --keep-case        : keep original letter case (default to lowercase)\n"
		"    -U, --upper-case       : convert to upper-case\n"
		"    -u, --keep-unicode     : keep original unicode form (default convert to canonical form)\n"
		"    -t, --keep-tones       : keep tones (default remove all tones/hat)\n"
		"    -d, --dict-path <path> : dictionaries path, default is " DICT_PATH "\n"
		"        --help             : show this message\n\n",
		argv[0]);

	return 0;
}

int vn_lang_tool_getopt_parse(int argc, char **argv, transform_option &opts)
{
	int option_code;
	while (~(option_code = getopt_long(argc, argv, "cUutd:", options, NULL)))
	{
		switch (option_code)
		{
		case 'c':
			opts.keep_case = true;
			break;
		case 'U':
			opts.to_upper = true;
			break;
		case 'u':
			opts.keep_unicode_form = true;
			break;
		case 't':
			opts.keep_tones = true;
			break;
		case 'd':
			opts.dict_path = optarg;
			break;
		default:
			return -1;
		}
	}

	return 0;
}

std::string do_transform(const std::string &s, const transform_option &opts)
{
	if (!VnLangTool::is_valid(s)) return "";

	std::vector< uint32_t > codepoints = VnLangTool::to_UTF(s);
	if (!opts.keep_case)
	{
		codepoints = VnLangTool::lower(codepoints);
	}
	if (!opts.keep_unicode_form)
	{
		codepoints = VnLangTool::normalize_NFD_UTF(codepoints);
	}
	if (!opts.keep_tones)
	{
		codepoints = VnLangTool::root(codepoints);
	}
	if (opts.to_upper)
	{
		codepoints = VnLangTool::upper(codepoints);
	}
	return VnLangTool::vector_to_string(codepoints);
}

int main(int argc, char **argv)
{
	transform_option opts;
	if (vn_lang_tool_getopt_parse(argc, argv, opts))
	{
		print_vn_lang_tool_usage(argc, argv);
		exit(EXIT_FAILURE);
	}

	if (0 > VnLangTool::init(opts.dict_path)) exit(EXIT_FAILURE);

	for (int i = optind; i < argc; ++i)
	{
		std::cout << do_transform(argv[i], opts) << std::endl;
	}
	if (optind == argc)
	{
		std::string line;
		std::ios::sync_with_stdio(false);
		std::cin.tie(nullptr);
		while (std::getline(std::cin, line))
		{
			std::cout << do_transform(line, opts) << '\n';
		}
	}
	return 0;
}
