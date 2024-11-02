library("pscl")

source("util.R")

nmes = loadNMES1988Csv("NMES1988")

generateHurdleNMES1988 = function(){
	nmes.hurdle = hurdle(visits ~ . - nvisits - ovisits - novisits - emergency, data = nmes)
	print(nmes.hurdle)

	storeRds(nmes.hurdle, "HurdleNMES1988")
	storeCsv(data.frame("visits" = predict(nmes.hurdle), "truncated(visits)" = predict(nmes.hurdle, type = "count"), check.names = FALSE), "HurdleNMES1988")
}

set.seed(42)

generateHurdleNMES1988()
