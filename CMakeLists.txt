cmake_minimum_required (VERSION 3.17.3 FATAL_ERROR)

project(temp)

set(CMAKE_SUPPRESS_REGENERATION true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_LIST_DIR}/cmake")

set(ARCH "x64" CACHE STRING "Specifies the architecture for the build, [x64, win32].")
set(ARCHITECTURES
	"x64"
	"win32"
)

if( NOT (${ARCH} IN_LIST ARCHITECTURES) )
	message(STATUS "Invalid architecture '${ARCH}', defaulting to x64.")
	set( ARCH "x64" )
endif()
message(STATUS "Architecture: ${ARCH}")

set(CMAKE_ROOT_DIRECTORY ${PROJECT_SOURCE_DIR})
set(CMAKE_BUILD_DIRECTORY ${PROJECT_BINARY_DIR})
set(CMAKE_CONFIG_DIRECTORY ${CMAKE_BUILD_DIRECTORY}/config)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BUILD_DIRECTORY}/bin)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BUILD_DIRECTORY}/bin)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BUILD_DIRECTORY}/bin)

#set(WOTWR_INSTALL_DIR ${CMAKE_ROOT_DIRECTORY}/bin/wotw_randomizer/${ARCH})
set(WOTWR_INSTALL_DIR C:/moon)
set(WOTWR_PROJECTS_DIR ${CMAKE_ROOT_DIRECTORY}/projects)
set(WOTWR_LIB_INCLUDE_DIR ${CMAKE_ROOT_DIRECTORY}/ext/include)
set(WOTWR_LIB_DIR ${CMAKE_ROOT_DIRECTORY}/ext/lib)

message(STATUS "WOTWR install directory: ${WOTWR_INSTALL_DIR}")
message(STATUS "root directory: ${CMAKE_ROOT_DIRECTORY}")
message(STATUS "build directory: ${CMAKE_BUILD_DIRECTORY}")
message(STATUS "config directory: ${CMAKE_CONFIG_DIRECTORY}")
message(STATUS "archive directory: ${CMAKE_ARCHIVE_OUTPUT_DIRECTORY}")
message(STATUS "library directory: ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
message(STATUS "runtime directory: ${CMAKE_RUNTIME_OUTPUT_DIRECTORY}")

set(CMAKE_INSTALL_DEFAULT_COMPONENT_NAME "runtime")

function(set_vc_structure_with_prefix pre root vc_files)
	foreach(FILE ${vc_files})
		# Get the directory of the source file
		get_filename_component(PARENT_DIR "${FILE}" DIRECTORY)

		# Remove common directory prefix to make the group
		string(REPLACE "${root}" "" GROUP "${PARENT_DIR}")

		# Make sure we are using windows slashes
		string(REPLACE "/" "\\" GROUP "${GROUP}")

		message(STATUS "${FILE} in ${pre}${GROUP}")
		source_group("${pre}${GROUP}" FILES "${FILE}")
	endforeach()
endfunction(set_vc_structure_with_prefix)

function(set_vc_structure root vc_files)
	foreach(FILE ${vc_files})
		# Get the directory of the source file
		get_filename_component(PARENT_DIR "${FILE}" DIRECTORY)

		# Remove common directory prefix to make the group
		string(REPLACE "${root}" "" GROUP "${PARENT_DIR}")

		# Make sure we are using windows slashes
		string(REPLACE "/" "\\" GROUP "${GROUP}")

		message(STATUS "${FILE} in ${pre}${GROUP}")
		source_group("${GROUP}" FILES "${FILE}")
	endforeach()
endfunction(set_vc_structure)

set_property(GLOBAL PROPERTY USE_FOLDERS ON)

find_program(GIT git)
if(NOT GIT)
    message(FATAL_ERROR "Cannot find git command line tool.")
else()
    message(STATUS "Fetching submodules.")
	execute_process(COMMAND ${GIT} submodule update --init --recursive)
endif()

file(READ "VERSION" ver)

string(REGEX MATCH "([0-9]*).([0-9]*).([0-9]*)" _ ${ver})
set(WOTWR_MAJOR_VERSION ${CMAKE_MATCH_1})
set(WOTWR_MINOR_VERSION ${CMAKE_MATCH_2})
set(WOTWR_PATCH_VERSION ${CMAKE_MATCH_3})

message("-- version: ${WOTWR_MAJOR_VERSION}.${WOTWR_MINOR_VERSION}.${WOTWR_PATCH_VERSION}")

set(WOTWR_VERSION ${WOTWR_MAJOR_VERSION}.${WOTWR_MINOR_VERSION}.${WOTWR_PATCH_VERSION})

set(SLN_NAME WotWRandomizer)
project (${SLN_NAME} VERSION ${WOTWR_VERSION} DESCRIPTION "WOTW Randomizer Base")
string(TOUPPER ${PROJECT_NAME} PROJECT_NAME_UPPER)

set(
	WOTWR_BASE_INCLUDES
	"${CMAKE_ROOT_DIRECTORY}/projects"
)

add_subdirectory("projects")

add_custom_target(
	INSTALL_RUNTIME
	ALL
	cmake -DCOMPONENT=runtime -DBUILD_TYPE=$<CONFIG> -P ${CMAKE_BUILD_DIRECTORY}/cmake_install.cmake
	DEPENDS Il2CppModLoader InjectDLL WinNetwork InjectLoader Injector RandoMainDLL TraceClientManaged SeedGenCli
)

set_target_properties(
	INSTALL_RUNTIME
	PROPERTIES
		FOLDER CMakePredefinedTargets
)
