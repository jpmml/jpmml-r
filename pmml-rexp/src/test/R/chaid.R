#install.packages("CHAID", repos="http://R-Forge.R-project.org")
library("CHAID")
library("r2pmml")

source("util.R")

audit = loadAuditCsv("Audit")

generateChaidAudit = function(){
	audit.chaid = chaid(Adjusted ~ Gender + Education + Employment + Marital + Occupation, data = audit)
	audit.chaid = decorate(audit.chaid)
	print(audit.chaid)

	adjusted = predict(audit.chaid, newdata = audit)
	probabilities = predict(audit.chaid, newdata = audit, type = "prob")

	storeRds(audit.chaid, "ChaidAudit")
	storeCsv(data.frame("Adjusted" = adjusted, "probability(0)" = probabilities[, 1], "probability(1)" = probabilities[, 2], check.names = FALSE), "ChaidAudit")
}

set.seed(42)

generateChaidAudit()

iris = loadIrisCsv("Iris")

generateChaidIris = function(){
	iris.chaid = chaid(Species ~ cut(Petal.Length, breaks = 5) + cut(Petal.Width, breaks = 5), data = iris)
	iris.chaid = decorate(iris.chaid)
	print(iris.chaid)

	species = predict(iris.chaid, newdata = iris)
	probabilities = predict(iris.chaid, newdata = iris, type = "prob")

	storeRds(iris.chaid, "ChaidIris")
	storeCsv(data.frame("Species" = species, "probability(setosa)" = probabilities[, 1], "probability(versicolor)" = probabilities[, 2], "probability(virginica)" = probabilities[, 3], check.names = FALSE), "ChaidIris")
}

set.seed(42)

generateChaidIris()
