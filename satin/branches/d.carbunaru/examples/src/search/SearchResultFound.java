package search;

import ibis.satin.Inlet;

class SearchResultFound extends Inlet {

    private static final long serialVersionUID = 1L;
    int pos;

    SearchResultFound(int pos) {
        this.pos = pos;
    }
}
