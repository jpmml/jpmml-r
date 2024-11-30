source("util.R")

realVec = c(-Inf, +Inf, NA, NaN, NULL)

storeRds(realVec, "RealVector")

integerVec = c(NA_integer_, as.integer(NaN), NULL)

storeRds(integerVec, "IntegerVector")

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

storeRds(dataFrame, "DataFrame")
