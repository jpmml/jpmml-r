library("rms")

source("util.R")

wine_quality = loadWineQualityCsv("WineQuality")

wine_quality$quality = as.ordered(cut(wine_quality$quality, breaks = c(0, 5, 6, 10), labels = c("Poor", "Normal", "Excellent")))

predictORMWineQuality = function(wine_quality.orm){
	probabilities = predict(wine_quality.orm, newdata = wine_quality, type = "fitted.ind")

	result = as.data.frame(probabilities)
	colnames(result) = c("probability(Poor)", "probability(Normal)", "probability(Excellent)")

	return (result)
}

generateORMWineQuality = function(family){
	wine_quality.orm = orm(quality ~ ., family = tolower(family), data = wine_quality)

	quality = predictORMWineQuality(wine_quality.orm)

	storeRds(wine_quality.orm, paste("ORM", family, "WineQuality", sep = ""))
	storeCsv(quality, paste("ORM", family, "WineQuality", sep = ""))
}

set.seed(42)

generateORMWineQuality(family = "Logistic")
generateORMWineQuality(family = "Probit")
generateORMWineQuality(family = "Cauchit")
