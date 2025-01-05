library("pscl")

source("util.R")

nmes = loadNMES1988Csv("NMES1988")

generateZeroInflNMES1988 = function(){
	nmes.zeroinfl = zeroinfl(visits ~ . - nvisits - ovisits - novisits - emergency, data = nmes)
	print(nmes.zeroinfl)

	storeRds(nmes.zeroinfl, "ZeroInflNMES1988")
	storeCsv(data.frame("visits" = predict(nmes.zeroinfl), "inflated(visits)" = predict(nmes.zeroinfl, type = "count"), check.names = FALSE), "ZeroInflNMES1988")
}

set.seed(42)

generateZeroInflNMES1988()