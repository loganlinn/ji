# ji

A web version of [SET card game](http://www.setgame.com/set) in Clojure(Script)
using [core.async](https://github.com/clojure/core.async) and WebSockets.

## Usage

### Running Locally

Dependencies
*   [leiningen](https://github.com/technomancy/leiningen)
*   [bundler](http://bundler.io/)
*   [shoreman](https://github.com/hecticjeff/shoreman) (optional)

```sh
git clone git@github.com:loganlinn/ji.git
cd ji
./bin/build
shoreman
```

Have a look at the [Procfile][Procfile] if you want to run it without shoreman.


## License

SET and its logo and slogan are registered trademarks of Cannei, LLC.

Copyright © 2013 Logan Linn

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
