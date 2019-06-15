cimport cython
from libcpp.vector cimport vector
from libcpp.string cimport string
from libcpp cimport bool

cdef extern from "<Python.h>":
    cdef char* PyUnicode_AsUTF8(object)
    cdef object PyUnicode_DecodeUTF8(char*, Py_ssize_t, char*)

cdef extern from "<tokenizer/config.h>":
    cdef string DICT_PATH

cdef extern from "<tokenizer/token.hpp>":
    cdef cppclass FullToken:
        string text

cdef extern from "<tokenizer/tokenizer.hpp>":
    cdef cppclass Tokenizer:
        @staticmethod
        Tokenizer &instance()
        int initialize(string, bool)
        vector[FullToken] segment_general(string, int)

cdef class PyTokenizer(object):
    cdef Tokenizer __CXX_Tokenizer

    def __cinit__(self, bool load_nontone_data = True):
        assert self.__CXX_Tokenizer.instance().initialize(DICT_PATH, load_nontone_data) >= 0

    @cython.boundscheck(False)
    @cython.wraparound(False)
    @cython.initializedcheck(False)
    @cython.nonecheck(False)
    cdef list __CXX_segment(self, str original_text, int tokenize_option):
        cdef string text = <string> PyUnicode_AsUTF8(original_text)

        cdef vector[FullToken] segmented = self.__CXX_Tokenizer.instance().segment_general(text, tokenize_option)

        cdef list tokens = []
        cdef int i, n = segmented.size()
        cdef string t

        for i from 0 <= i < n:
            t = segmented[i].text
            tokens.append(PyUnicode_DecodeUTF8(t.c_str(), t.length(), NULL))

        return tokens

    @cython.boundscheck(False)
    @cython.wraparound(False)
    @cython.initializedcheck(False)
    @cython.nonecheck(False)
    def word_tokenize(self, str original_text, int tokenize_option = 0):
        return self.__CXX_segment(original_text, tokenize_option)
