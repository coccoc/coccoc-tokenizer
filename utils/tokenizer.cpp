#include <vector>
#include <getopt.h>
#include <tokenizer/tokenizer.hpp>
#include <tokenizer/config.h>

struct tokenizer_option
{
	bool for_transforming;
	bool no_sticky;
	int tokenize_option;
	bool verbose;
	const char *dict_path;

	tokenizer_option()
	    : for_transforming(false),
	      no_sticky(false),
	      tokenize_option(Tokenizer::TOKENIZE_NORMAL),
	      verbose(false),
	      dict_path(DICT_PATH)
	{
	}
};

// clang-format off
static struct option options[] = {
	{ "help"         , no_argument      , NULL,  0  },
	{ "for-transform", no_argument      , NULL, 't' },
	{ "no-sticky"    , no_argument      , NULL, 'n' },
	{ "url"          , no_argument      , NULL, 'u' },
	{ "host"         , no_argument      , NULL, 'h' },
	{ "verbose"      , no_argument      , NULL, 'v' },
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
		"    -t, --for-transform    : segment for transforming\n"
		"    -n, --no-sticky        : do not split sticky text\n"
		"    -u, --url              : segment URL\n"
		"    -h, --host             : segment HOST\n"
		"    -v, --verbose          : print token details\n"
		"    -d, --dict-path <path> : dictionaries path, default is " DICT_PATH "\n"
		"        --help             : show this message\n\n",
		argv[0]);

	return 0;
}

int tokenizer_getopt_parse(int argc, char **argv, tokenizer_option &opts)
{
	int option_code;
	while (~(option_code = getopt_long(argc, argv, "tnuhvd:", options, NULL)))
	{
		switch (option_code)
		{
		case 't':
			opts.for_transforming = true;
			break;
		case 'n':
			opts.no_sticky = true;
			break;
		case 'u':
			opts.tokenize_option = Tokenizer::TOKENIZE_URL;
			break;
		case 'h':
			opts.tokenize_option = Tokenizer::TOKENIZE_HOST;
			break;
		case 'v':
			opts.verbose = true;
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

	if (0 > Tokenizer::instance().initialize(opts.dict_path, !opts.no_sticky)) exit(EXIT_FAILURE);

	auto process = [&opts](const std::string &text)
	{
		std::vector< FullToken > res =
			Tokenizer::instance().segment(text, opts.for_transforming, opts.tokenize_option);
		for (FullToken &it : res)
		{
			if (opts.verbose)
			{
				std::cout << it.to_string() << '\t';
			}
			else
			{
				std::cout << it.text << '\t';
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
