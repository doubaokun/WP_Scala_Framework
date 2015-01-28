package com.whitepages.framework.client

trait SOLRTestData {
  val data = """{"centroid":
  ["lat",
  "47.601234",
  "lon",
  "-122.33047",
  "latlon",
  "47.601234,-122.330466",
  "dist",
  "48.2803"
  ],
  "exact":{"exactMatchCount":0},
  "response":
    {"docs":
      [{"addr_location":
      ["Seattle WA 98107 2025 981072025"],
      "is_suppression":false,
      "is_user_generated":false,
      "listing_id":
      "S|377c23b5-962b-4d20-bea7-0858460e6584",
      "person_name_fname":"Dean",
      "person_name_lname":"Jocic",
      "score":9.136872
    }
      ],
      "maxScore":9.136872,
      "numFound":1,
      "start":0
    },
  "responseHeader":
    {"QTime":15,
      "params":
    {"fps_fl":
      "addr_location,person_name_fname,person_name_lname,listing_id,is_user_generated,is_suppression,score",
      "fps_fname":"dan*",
      "fps_lname":"jazek",
      "fps_rows":"10",
      "fps_search_id":"22070690954565913736",
      "fps_start":"0",
      "fps_where":"\"seattle, wa\"",
      "fq":
      "{!switch case=$fq_simple default=$fq_bbox v=$fps_latlong}",
      "indent":"off",
      "q":
      "( _query_:\"{!wp_optional df='addr_location_clean_i' qs=1 v=$fps_where}\"^6.2 OR _query_:\"{!wp_optional df='addr_location_i' qs=1 v=$fps_where}\"^6.2 OR _query_:\"*:*\" ) ( _query_:\"{!wp_dismax qf=person_name_fname_clean_i v=$fps_fname}\"^3.9 OR _query_:\"{!wp_dismax qf=person_name_fname_i v=$fps_fname}\"^7.2 OR _query_:\"{!wp_dismax qf=person_name_finitial_i v=$fps_fname}\"^0.6 OR _query_:\"{!wp_dismax qf=person_name_fname_phonetic_i v=$fps_fname}\"^0.9 OR _query_:\"{!wp_stringprefix f=person_name_fname_i v=$fps_fname}\"^6.5 ) ( _query_:\"{!wp_dismax qf=person_name_lname_i v=$fps_lname}\"^8.3 OR _query_:\"{!wp_dismax qf=person_name_lname_phonetic_i v=$fps_lname}\"^8.6 )",
      "qt":"findperson",
      "rows":"105",
      "show_suppressed":"false",
      "show_ugc":"true",
      "timeAllowed":"4750",
      "version":"2.2",
      "wt":"json"
    },
      "status":0
    },
  "scoretruncation":
    {"discardedDocs":10,
      "fps_rows":10,
      "fps_start":0,
      "origNumFound":11,
      "percentOfMax":30.0,
      "percentOfMaxDefault":30.0,
      "truncationMinAcceptableScore":2.7410617
    },
  "suppressions":
    {"preSuppressionMaxScore":9.136872,
      "removedUgcCount":0,
      "suppressedDocCount":0,
      "suppressionDocCount":0
    }
}"""


}
