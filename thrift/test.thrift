/*
   This file is used only to generate files into
      src/test/scala/com/whitepage/generated
   These are used for project tests.
*/

namespace scala com.whitepages.generated

struct Request {
 1: string address1,
 2: string address2,
 3: string location
}

struct Response {
 1: string zip,
 2: string county
}

// CaseCheckerSubObject and CaseChecker objects used to test case conversion
// of field names in JSON input and output
struct CaseCheckerSubObject {
 1: optional i32 simple,
 2: optional i32 a_name,
}

struct CaseChecker {
 1: optional i32 simple,
 2: optional i32 a_name,
 9: optional CaseCheckerSubObject sub_object
}

exception ServerException {
 1: optional string classname,
 2: optional string message,
 3: optional list<string> backtrace
}

struct Compound {
  1: string c1,
  2: i16 c2
}

union UTest {
   1: string s,
   2: Compound c,
   3: map<Compound,string> m
}

service Test {

   Response testcmd(1:Request request)

   i32 testcmd1(1:string addr1, 2: string addr2)

   i32 testcmd2(1:list<string> names)

   UTest testcmd3(1:UTest in1)

   string shortIdToUuid(1: string ids) throws (1: ServerException e)

   // casechecker method used to change case conversions on field names
   // (using CaseChecker struct)
   CaseChecker casechecker(1: CaseChecker case_checker)

   // camelCase and snake_case methods used to check case conversion on
   // method names
   i32 camelCase(1: i32 i)
   i32 snake_case(1: i32 i)
}

enum Enum {
  CoolValue = 42,
  OtherValue
}

struct FullTest {
  1: optional Enum enum_field,
  2: optional list<string> strings,
  3: optional Response response
}

struct ShortTest {
  1: required i16 required_short,
  2: optional i16 optional_short
}

struct HasLong {
  1: optional i64 long
}

struct HasMap {
  1: optional map<string, HasLong> mappy
}

struct HasHasMap {
  1: optional HasMap hasMap
}

struct HasList {
  1: optional list<HasLong> hasLongs
}

struct HasHasList {
 1: optional list<HasLong> hasLongs,
 2: optional HasList hasList
}
