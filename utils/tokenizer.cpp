#include <vector>
#include <getopt.h>
#include <tokenizer/tokenizer.hpp>
#include <tokenizer/config.h>

#define FORMAT_TSV 0
#define FORMAT_ORIGINAL 1
#define FORMAT_VERBOSE 2

struct tokenizer_option
{
	bool no_sticky;
	int tokenize_option;
	int format;
	const char *dict_path;

	tokenizer_option()
	    : no_sticky(false),
	      tokenize_option(Tokenizer::TOKENIZE_NORMAL),
	      format(FORMAT_TSV),
	      dict_path(DICT_PATH)
	{
	}
};

// clang-format off
static struct option options[] = {
	{ "help"         , no_argument      , NULL,  0  },
	{ "no-sticky"    , no_argument      , NULL, 'n' },
	{ "url"          , no_argument      , NULL, 'u' },
	{ "host"         , no_argument      , NULL, 'h' },
	{ "format"       , required_argument, NULL, 'f' },
	{ "dict-path"    , required_argument, NULL, 'd' },
	{  NULL          , 0                , NULL,  0  }
};
// clang-format on

int print_tokenizer_usage(int argc, char **argv)
{
	fprintf(stderr,
		"Usage:\n"
		"    %s [TEXT]...\n"
		"\n"
		"Options:\n"
		"    -n, --no-sticky        : do not split sticky text\n"
		"    -u, --url              : segment URL\n"
		"    -h, --host             : segment HOST\n"
		"    -f, --format <format>  : output format (tsv, original, verbose)\n"
		"    -d, --dict-path <path> : dictionaries path, default is " DICT_PATH "\n"
		"        --help             : show this message\n"
		"\n"
		"Output formats:\n"
		"    tsv                    : normalized lowercased tokens separated by TAB symbol\n"
		"    original               : spaces inside single tokens are changed into underscores, the rest is like in original text\n"
		"    verbose                : verbose information about each token (TAB separated)\n\n",
		argv[0]);

	return 0;
}

int tokenizer_getopt_parse(int argc, char **argv, tokenizer_option &opts)
{
	int option_code;
	while (~(option_code = getopt_long(argc, argv, "nuhf:d:", options, NULL)))
	{
		switch (option_code)
		{
		case 'n':
			opts.no_sticky = true;
			break;
		case 'u':
			opts.tokenize_option = Tokenizer::TOKENIZE_URL;
			break;
		case 'h':
			opts.tokenize_option = Tokenizer::TOKENIZE_HOST;
			break;
		case 'f':
			if (0 == strcmp(optarg, "tsv"))
			{
				opts.format = FORMAT_TSV;
			}
			else if (0 == strcmp(optarg, "original"))
			{
				opts.format = FORMAT_ORIGINAL;
			}
			else if (0 == strcmp(optarg, "verbose"))
			{
				opts.format = FORMAT_VERBOSE;
			}
			else
			{
				fprintf(stderr, "Error: Unsupported output format '%s'.\n\n", optarg);
				return -1;
			}
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

int main(int argc, char **argv)
{
	tokenizer_option opts;

	if (tokenizer_getopt_parse(argc, argv, opts))
	{
		print_tokenizer_usage(argc, argv);
		exit(EXIT_FAILURE);
	}

	if (0 > Tokenizer::instance().initialize(opts.dict_path, !opts.no_sticky))
	{
		exit(EXIT_FAILURE);
	}

	auto process = [&opts](const std::string &text)
	{
		std::vector< FullToken > res = opts.format != FORMAT_ORIGINAL ?
			Tokenizer::instance().segment(text, false, opts.tokenize_option) :
			Tokenizer::instance().segment_original(text, opts.tokenize_option);

		if (opts.format == FORMAT_ORIGINAL)
		{
			size_t i = 0;

			for (/* void */; i < res.size(); ++i)
			{
				size_t punct_start = (i > 0) ? res[i - 1].original_end : 0;
				size_t punct_len = res[i].original_start - punct_start;

				if (punct_len > 0)
				{
					std::cout << text.substr(punct_start, punct_len);
				} else if (i > 0) {
					std::cout << ' '; // avoid having tokens sticked together
				}

				std::cout << res[i].text;
			}

			size_t punct_start = (i > 0) ? res[i - 1].original_end : 0;
			size_t punct_len = text.size() - punct_start;

			if (punct_len > 0)
			{
				std::cout << text.substr(punct_start, punct_len);
			}
		}
		else
		{
			for (size_t i = 0; i < res.size(); ++i)
			{
				if (i > 0)
				{
					std::cout << '\t';
				}

				std::cout << ((opts.format == FORMAT_VERBOSE) ? res[i].to_string() : res[i].text);
			}
		}

		std::cout << std::endl;
	};

	for (int i = optind; i < argc; ++i)
	{
		process(argv[i]);
	}

	if (optind == argc)
	{
		std::string line;
		while (std::getline(std::cin, line))
		{
			process(line);
		}
	}

	return 0;
}
