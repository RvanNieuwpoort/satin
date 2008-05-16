package search;

import ibis.satin.SatinObject;
import ibis.satin.Inlet;

class SearchResultFound extends Inlet {
   int pos;

   SearchResultFound(int pos) {
      this.pos = pos;
   }
}
