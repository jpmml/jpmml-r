source("util.R")

realVec = c(-Inf, +Inf, NA, NaN, NULL)
print(realVec)
print(attributes(realVec))

storeRds(realVec, "RealVector")

integerVec = c(NA_integer_, as.integer(NaN), NULL)
print(integerVec)
print(attributes(integerVec))

storeRds(integerVec, "IntegerVector")

stringVec = c("alpha", "beta", "gamma")
print(stringVec)
print(attributes(stringVec))

storeRds(stringVec, "StringVector")

factorVec = factor(c("alpha", "beta", "gamma"))
print(attributes(factorVec))

storeRds(factorVec, "FactorVector")

namedList = list(
	"real" = 1.0,
	"real_vector" = c(-1.0, 0.0, 1.0),
	"integer" = as.integer(1),
	"integer_vector" = as.integer(c(-1, 0, 1)),
	"logical" = TRUE,
	"logical_vector" = c(FALSE, TRUE),
	"string" = "alpha",
	"string_vector" = c("alpha", "beta", "gamma"),
	"factor" = as.factor("alpha"),
	"factor_vector" = as.factor(c("alpha", "beta", "gamma"))
)
print(namedList)
print(attributes(namedList))

storeRds(namedList, "NamedList")

dataFrame = data.frame(
	"real" = c(-1.0, 0.0, 1.0),
	"integer" = as.integer(c(-1, 0, 1)),
	"logical" = c(FALSE, NA, TRUE),
	"string" = c("alpha", "beta", "gamma"),
	"factor" = as.factor(c("alpha", "beta", "gamma")),
	stringsAsFactors = FALSE
)
print(dataFrame)
print(attributes(dataFrame))

storeRds(dataFrame, "DataFrame")
