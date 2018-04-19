library("evtree")
library("r2pmml")

source("util.R")

audit = loadAuditCsv("Audit")

generateEvTreeAudit = function(){
	audit.evtree = evtree(Adjusted ~ ., data = audit, control = evtree.control(seed = 13))
	audit.evtree = decorate(audit.evtree)
	print(audit.evtree)

	adjusted = predict(audit.evtree, newdata = audit)

	storeRds(audit.evtree, "EvTreeAudit")
	storeCsv(data.frame("Adjusted" = adjusted), "EvTreeAudit")
}

set.seed(42)

generateEvTreeAudit()

iris = loadIrisCsv("Iris")

generateEvTreeIris = function(){
	iris.evtree = evtree(Species ~ ., data = iris, control = evtree.control(seed = 13))
	iris.evtree = decorate(iris.evtree)
	print(iris.evtree)

	species = predict(iris.evtree, newdata = iris)

	storeRds(iris.evtree, "EvTreeIris")
	storeCsv(data.frame("Species" = species), "EvTreeIris")
}

set.seed(42)

generateEvTreeIris()

auto = loadAutoCsv("Auto")

generateEvTreeAuto = function(){
	auto.evtree = evtree(mpg ~ ., data = auto, control = evtree.control(seed = 13))
	auto.evtree = decorate(auto.evtree)
	print(auto.evtree)

	mpg = predict(auto.evtree, newdata = auto)

	storeRds(auto.evtree, "EvTreeAuto")
	storeCsv(data.frame("mpg" = mpg), "EvTreeAuto")
}

set.seed(42)

generateEvTreeAuto()

wine_quality = loadWineQualityCsv("WineQuality")

generateEvTreeWineQuality = function(){
	wine_quality.evtree = evtree(quality ~ ., data = wine_quality, control = evtree.control(seed = 13))
	wine_quality.evtree = decorate(wine_quality.evtree)

	quality = predict(wine_quality.evtree, newdata = wine_quality)

	storeRds(wine_quality.evtree, "EvTreeWineQuality")
	storeCsv(data.frame("quality" = quality), "EvTreeWineQuality")
}

set.seed(42)

generateEvTreeWineQuality()