PROJECT (coccoc-tokenizer)
CMAKE_MINIMUM_REQUIRED (VERSION 2.8)
INCLUDE (CMakeMacro.cmake)

INCLUDE_DIRECTORIES (${PROJECT_BINARY_DIR}/auto)
INCLUDE_DIRECTORIES (tokenizer)

ADD_EXECUTABLE (dict_compiler utils/dict_compiler.cpp)
ADD_EXECUTABLE (tokenizer utils/tokenizer.cpp)
ADD_EXECUTABLE (vn_lang_tool utils/vn_lang_tool.cpp)

SET (MULTITERM_DICT_DUMP "multiterm_trie.dump")
SET (SYLLABLE_DICT_DUMP "syllable_trie.dump")
SET (NONTONE_PAIR_DICT_DUMP "nontone_pair_freq_map.dump")

LIST (APPEND DICT_DUMPS ${MULTITERM_DICT_DUMP})
LIST (APPEND DICT_DUMPS ${SYLLABLE_DICT_DUMP})
LIST (APPEND DICT_DUMPS ${NONTONE_PAIR_DICT_DUMP})

# We don't use GLOB here because it also takes hidden files and we are too lazy to cope with it
LIST (APPEND DICT_SOURCES "${CMAKE_SOURCE_DIR}/dicts/tokenizer/acronyms")
LIST (APPEND DICT_SOURCES "${CMAKE_SOURCE_DIR}/dicts/tokenizer/chemical_comp")
LIST (APPEND DICT_SOURCES "${CMAKE_SOURCE_DIR}/dicts/tokenizer/Freq2NontoneUniFile")
LIST (APPEND DICT_SOURCES "${CMAKE_SOURCE_DIR}/dicts/tokenizer/keyword.freq")
LIST (APPEND DICT_SOURCES "${CMAKE_SOURCE_DIR}/dicts/tokenizer/nontone_pair_freq")
LIST (APPEND DICT_SOURCES "${CMAKE_SOURCE_DIR}/dicts/tokenizer/special_token.strong")
LIST (APPEND DICT_SOURCES "${CMAKE_SOURCE_DIR}/dicts/tokenizer/special_token.weak")
LIST (APPEND DICT_SOURCES "${CMAKE_SOURCE_DIR}/dicts/tokenizer/vndic_multiterm")

ADD_CUSTOM_TARGET (compile_dict ALL DEPENDS ${DICT_DUMPS})
ADD_CUSTOM_COMMAND (
	OUTPUT ${DICT_DUMPS}
	COMMAND ${CMAKE_BINARY_DIR}/dict_compiler "${CMAKE_SOURCE_DIR}/dicts" "${CMAKE_BINARY_DIR}"
	DEPENDS dict_compiler ${DICT_SOURCES}
	VERBATIM
)

INSTALL (TARGETS tokenizer DESTINATION bin)
INSTALL (TARGETS vn_lang_tool DESTINATION bin)
INSTALL (TARGETS dict_compiler DESTINATION bin)
INSTALL (DIRECTORY tokenizer/ DESTINATION include/tokenizer FILES_MATCHING PATTERN "*.h")
INSTALL (DIRECTORY tokenizer/ DESTINATION include/tokenizer FILES_MATCHING PATTERN "*.hpp")
INSTALL (DIRECTORY tokenizer/ DESTINATION include/tokenizer FILES_MATCHING PATTERN "*.tcc")
INSTALL_TEMPLATE (tokenizer/config.h.in DESTINATION include/tokenizer)
INSTALL (DIRECTORY dicts/ DESTINATION share/tokenizer/dicts_text)
INSTALL (DIRECTORY dicts/vn_lang_tool/ DESTINATION share/tokenizer/dicts)
INSTALL (FILES ${CMAKE_BINARY_DIR}/${MULTITERM_DICT_DUMP} DESTINATION share/tokenizer/dicts)
INSTALL (FILES ${CMAKE_BINARY_DIR}/${SYLLABLE_DICT_DUMP} DESTINATION share/tokenizer/dicts)
INSTALL (FILES ${CMAKE_BINARY_DIR}/${NONTONE_PAIR_DICT_DUMP} DESTINATION share/tokenizer/dicts)

IF (${BUILD_JAVA})
	ADD_CUSTOM_TARGET (compile_java ALL DEPENDS ${CMAKE_BINARY_DIR}/coccoc-tokenizer.jar)
	ADD_CUSTOM_COMMAND (
		OUTPUT ${CMAKE_BINARY_DIR}/coccoc-tokenizer.jar
		COMMAND ${CMAKE_SOURCE_DIR}/java/build_java.sh ${CMAKE_BINARY_DIR}
		VERBATIM
	)
	INSTALL (FILES ${CMAKE_BINARY_DIR}/coccoc-tokenizer.jar DESTINATION share/java)
	INSTALL (FILES ${CMAKE_BINARY_DIR}/libcoccoc_tokenizer_jni.so DESTINATION lib)
ENDIF ()

IF (${BUILD_PYTHON})
	# XXX Some build files in this target are generated inside source tree, should fix later
	ADD_CUSTOM_TARGET (compile_python ALL DEPENDS ${CMAKE_BINARY_DIR}/python/lib)
	ADD_CUSTOM_COMMAND (
		OUTPUT ${CMAKE_BINARY_DIR}/python/lib
		COMMAND ${CMAKE_SOURCE_DIR}/python/build_python.sh ${CMAKE_BINARY_DIR} install --prefix=${CMAKE_BINARY_DIR}/python
		VERBATIM
	)
	INSTALL (DIRECTORY ${CMAKE_BINARY_DIR}/python/lib/ DESTINATION lib)
ENDIF ()
