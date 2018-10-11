# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)


set(FFMPEG_DIR ${CMAKE_SOURCE_DIR}/../FFMPEG)



add_library( # Sets the name of the library.
             ffmpeg-native

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             ${CMAKE_SOURCE_DIR}/src/main/cpp/ffmpeg-native.cpp )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

MESSAGE(STATUS "====================FFMEGP DIR " ${FFMPEG_DIR})
MESSAGE(STATUS "====================CMAKE_SOURCE_DIR DIR " ${CMAKE_SOURCE_DIR})
MESSAGE(STATUS "====================CPU MODEL " ${ANDROID_ABI})

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_include_directories(ffmpeg-native PRIVATE
                           ${FFMPEG_DIR}/include)



add_library(ffmpeg SHARED IMPORTED)
set_target_properties(ffmpeg PROPERTIES IMPORTED_LOCATION
    ${FFMPEG_DIR}/lib/armeabi/libffmpeg.so)



target_link_libraries(ffmpeg-native
                      ffmpeg
                      ${log-lib}
                      android)








