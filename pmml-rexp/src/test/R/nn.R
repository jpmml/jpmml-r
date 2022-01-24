library("neuralnet")

source("util.R")

wine_quality = loadWineQualityCsv("WineQuality")

# See https://stackoverflow.com/questions/17794575
wine_quality.formula = reformulate(setdiff(colnames(wine_quality), "quality"), response = "quality")

generateNNWineQuality = function(){
	wine_quality.nn = neuralnet(formula = wine_quality.formula, data = wine_quality, hidden = c(6, 3), threshold = 5, lifesign = "full")
	print(wine_quality.nn)

	result = compute(wine_quality.nn, wine_quality[, -ncol(wine_quality)])

	storeRds(wine_quality.nn, "NNWineQuality")
	storeCsv(data.frame("quality" = result$net.result[, 1]), "NNWineQuality")
}

set.seed(42)

generateNNWineQuality()