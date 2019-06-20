from __future__ import absolute_import, division, print_function
from Cython.Distutils import build_ext
from distutils.core import setup
from distutils.extension import Extension

ext_modules = [
    Extension(
        name="CocCocTokenizer",
        sources=["CocCocTokenizer.pyx"],
        extra_compile_args=["-Wno-cpp", "-Wno-unused-function", "-O2", "-march=native"],
        extra_link_args=["-O2", "-march=native"],
        language="c++",
    )
]

setup(
    name = "CocCocTokenizer",
    version = "1.4",
    description = "high performance tokenizer for Vietnamese language",
    long_description = "high performance tokenizer for Vietnamese language",
    author = "Le Anh Duc",
    author_email = "anhducle@coccoc.com",
    url = "https://github.com/coccoc/coccoc-tokenizer",
    license = "GNU Lesser General Public License v3",
    ext_modules = ext_modules,
    cmdclass = { "build_ext": build_ext }
)
