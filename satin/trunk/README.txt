** Satin README **

Satin is part of Ibis, an open source Java grid software project of
the Computer Systems group of the Computer Science department of the
Faculty of Sciences at the Vrije Universiteit, Amsterdam, The Netherlands.
Satin extends Java with Cilk like primitives, that make it very convenient
for the programmer to write divide and conquer style programs. Unlike
manager/worker programs, divide-and-conquer algorithms operate by
recursively dividing a problem into smaller subproblems.
This recursive subdivision goes on until the remaining subproblem becomes
trivial to solve. After solving subproblems, their results are recursively
recombined until the final solution is assembled.
Due to it's hierarchical nature, the divide-and-conquer model maps
cleanly to grid systems, which also tend to have an hierarchical
structure. Satin contains a efficient and simple load-balancing algorithm,
Cluster-aware Random Stealing (CRS), which outperforms existing load-balancing
strategies on multi-cluster systems.
In addition, Satin also provides efficient fault-tolerance,
malleability (e.g. the ability to cope with dynamically changing number
of processors) and migration in a way that is transparent to the application.

Satin is built on the Ibis Portability Layer (IPL), which is included in this
release. Some example Satin applications are provided in the "examples"
directory.

Satin is free software. See the file "LICENSE.txt" for copying permissions.

The users's guide in the docs directory ("docs/usersguide.pdf") explains
how to compile and run your Satin application.

The programmer's manual ("docs/progman.pdf") contains a detailed
description of the Satin Application Programmer's interface (API),
illustrated with example code fragments.

The javadoc of Satin is available in "javadoc/index.html".

Ibis has its own web-site: http://www.cs.vu.nl/ibis/.  There, you can
find more Ibis documentation, papers, application sources.

The current Satin source repository tree is accessible through SVN at
"https://gforge.cs.vu.nl/svn/ibis/satin/trunk".  You can check it
out anonymously using the command
"svn checkout https://gforge.cs.vu.nl/svn/ibis/satin/trunk satin".

The file BUGS.txt contains information for filing bug reports.

** Third party libraries included with Satin **

This product includes software developed by the Apache Software
Foundation (http://www.apache.org/).

The BCEL copyright notice lives in "notices/LICENSE.bcel.txt".  The
Log4J copyright notice lives in "notices/LICENSE.log4j.txt".  The
Commons copyright notice lives in notices/LICENSE.apache-2.0.txt".

This product includes jstun, which is distributed with a dual license,
one of which is version 2.0 of the Apache license. It lives in
"notices/LICENSE.apache-2.0.txt".

This product includes the UPNP library from SuperBonBon Industries. Its
license lives in "notices/LICENSE.apache-2.0.txt".

This product includes the trilead SSH-2 library. Its license
lives in "notices/LICENSE.trilead.txt".

This product includes some of the JavaGAT libraries. Its license
lives in "notices/LICENSE.javagat.txt".

This product includes software developed by TouchGraph LLC
(http://www.touchgraph.com/). Its license lives in
"notices/LICENSE.TG.txt".
