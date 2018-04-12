library("caret")
library("caretEnsemble")

source("util.R")

classControl = trainControl(number = 5, classProbs = TRUE)
regControl = trainControl(number = 5)

auto = loadAutoCsv("Auto")
auto$origin = as.integer(auto$origin)

generateCaretEnsembleAuto = function(){
	models = caretList(mpg ~ ., data = auto, trControl = regControl, methodList = c("rpart", "lm"))

	auto.ensemble = caretEnsemble(models)
	print(auto.ensemble)

	mpg = predict(auto.ensemble, newdata = auto)

	storeRds(auto.ensemble, "CaretEnsembleAuto")
	storeCsv(data.frame(".outcome" = mpg), "CaretEnsembleAuto")
}

set.seed(42)

generateCaretEnsembleAuto()

versicolor = loadVersicolorCsv("Versicolor")

generateCaretEnsembleVersicolor = function(){
	models = caretList(Species ~ Petal.Length + Petal.Width, data = versicolor, trControl = classControl, methodList = c("rpart", "glm"))

	versicolor.ensemble = caretEnsemble(models)
	print(versicolor.ensemble)

	species = predict(versicolor.ensemble, newdata = versicolor)
	probabilities = predict(versicolor.ensemble, newdata = versicolor, type = "prob")

	storeRds(versicolor.ensemble, "CaretEnsembleVersicolor")
	storeCsv(data.frame(".outcome" = species, "probability(no)" = (1 - probabilities), "probability(yes)" = probabilities, check.names = FALSE), "CaretEnsembleVersicolor")
}

set.seed(42)

generateCaretEnsembleVersicolor()