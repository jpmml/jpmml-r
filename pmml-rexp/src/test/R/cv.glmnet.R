library("caret")
library("glmnet")
library("r2pmml")

source("util.R")

versicolor = loadVersicolorCsv("Versicolor")

versicolor_x.raw = as.matrix(versicolor[, -ncol(versicolor)])
versicolor_y = versicolor[, ncol(versicolor)]

versicolor_x.preProc = preProcess(versicolor_x.raw, method = c("scale"))

versicolor_x = predict(versicolor_x.preProc, newdata = versicolor_x.raw)

generateCrossValLogNetVersicolor = function(){
	versicolor.glmnet = cv.glmnet(x = versicolor_x, y = versicolor_y, family = "binomial")
	versicolor.glmnet = decorate(versicolor.glmnet, preProcess = versicolor_x.preProc)
	print(versicolor.glmnet)

	species = predict(versicolor.glmnet, newx = versicolor_x, s = "lambda.1se", type = "class")
	probability = predict(versicolor.glmnet, newx = versicolor_x, s = "lambda.1se", type = "response")

	storeRds(versicolor.glmnet, "CrossValLogNetVersicolor")
	storeCsv(data.frame("_target" = species[, 1], "probability(no)" = (1 - probability[, 1]), "probability(yes)" = probability[, 1], check.names = FALSE), "CrossValLogNetVersicolor")

}

set.seed(42)

generateCrossValLogNetVersicolor()

wine_quality = loadWineQualityCsv("WineQuality")

wine_quality_x = as.matrix(wine_quality[, -ncol(wine_quality)])
wine_quality_y = wine_quality[, ncol(wine_quality)]

generateCrossValElNetWineQuality = function(){
	wine_quality.glmnet = cv.glmnet(x = wine_quality_x, y = wine_quality_y, family = "gaussian")
	print(wine_quality.glmnet)

	wine_quality = predict(wine_quality.glmnet, newx = wine_quality_x, s = "lambda.1se")

	storeRds(wine_quality.glmnet, "CrossValElNetWineQuality")
	storeCsv(data.frame("_target" = wine_quality[, 1]), "CrossValElNetWineQuality")
}

set.seed(42)

generateCrossValElNetWineQuality()

wine_color = loadWineColorCsv("WineColor")

wine_color_x = as.matrix(wine_color[, -ncol(wine_color)])
wine_color_y = wine_color[, ncol(wine_color)]

generateCrossValLogNetWineColor = function(){
	wine_color.glmnet = cv.glmnet(x = wine_color_x, y = wine_color_y, family = "binomial")
	print(wine_color.glmnet)

	color = predict(wine_color.glmnet, newx = wine_color_x, s = "lambda.1se", type = "class")
	probability = predict(wine_color.glmnet, newx = wine_color_x, s = "lambda.1se", type = "response")

	storeRds(wine_color.glmnet, "CrossValLogNetWineColor")
	storeCsv(data.frame("_target" = color[, 1], "probability(red)" = (1 - probability[, 1]), "probability(white)" = probability[, 1], check.names = FALSE), "CrossValLogNetWineColor")
}

set.seed(42)

generateCrossValLogNetWineColor()
