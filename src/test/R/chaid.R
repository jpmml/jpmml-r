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

	storeRds(audit.chaid, "ChaidAudit")
	storeCsv(data.frame("Adjusted" = adjusted), "ChaidAudit")
}

set.seed(42)

generateChaidAudit()

iris = loadIrisCsv("Iris")

generateChaidIris = function(){
	iris.chaid = chaid(Species ~ cut(Petal.Length, breaks = 5) + cut(Petal.Width, breaks = 5), data = iris)
	iris.chaid = decorate(iris.chaid)
	print(iris.chaid)

	species = predict(iris.chaid, newdata = iris)

	storeRds(iris.chaid, "ChaidIris")
	storeCsv(data.frame("Species" = species), "ChaidIris")
}

set.seed(42)

generateChaidIris()
