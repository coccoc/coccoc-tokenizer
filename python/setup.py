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
        include_dirs=["."],
    )
]

setup(
    name="coccoc-tokenizer", ext_modules=ext_modules, cmdclass={"build_ext": build_ext}
)
