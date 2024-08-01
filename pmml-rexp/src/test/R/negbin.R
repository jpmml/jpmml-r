library("MASS")

source("util.R")

visit = loadVisitCsv("Visit")

generateNegbinFormulaVisit = function(){
	visit.negbin = glm.nb(docvis ~ ., data = visit)
	print(visit.negbin)

	docvis = predict(visit.negbin, newdata = visit, type = "response")

	storeRds(visit.negbin, "NegbinFormulaVisit")
	storeCsv(data.frame("docvis" = docvis), "NegbinFormulaVisit")
}

generateNegbinFormulaVisit()