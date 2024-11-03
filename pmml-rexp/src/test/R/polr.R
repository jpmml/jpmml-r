library("MASS")

source("util.R")

wine_quality = loadWineQualityCsv("WineQuality")

wine_quality$quality = as.ordered(cut(wine_quality$quality, breaks = c(0, 5, 6, 10), labels = c("Poor", "Normal", "Excellent")))

predictPOLRWineQuality = function(wine_quality.polr){
	quality = predict(wine_quality.polr, type = "class")
	probabilities = predict(wine_quality.polr, type = "probs")

	result = data.frame(quality)
	names(result) = c("quality")
	result = data.frame(result, "probability(Poor)" = probabilities[, 1], "probability(Normal)" = probabilities[, 2], "probability(Excellent)" = probabilities[, 3], check.names = FALSE)

	return (result)
}

generatePOLRWineQuality = function(method){
	wine_quality.polr = polr(quality ~ ., method = tolower(method), data = wine_quality)
	print(wine_quality.polr)

	quality = predictPOLRWineQuality(wine_quality.polr)

	storeRds(wine_quality.polr, paste("POLR", method, "WineQuality", sep = ""))
	storeCsv(quality, paste("POLR", method, "WineQuality", sep = ""))
}

set.seed(42)

generatePOLRWineQuality(method = "Logistic")
generatePOLRWineQuality(method = "Probit")
generatePOLRWineQuality(method = "LogLog")
generatePOLRWineQuality(method = "CLogLog")
generatePOLRWineQuality(method = "Cauchit")
