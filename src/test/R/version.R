print("R version:")
print(paste(R.version$major, R.version$minor, sep = "."))

print("R package versions:")
packages = c("caret", "gbm", "IsolationForest", "party", "randomForest", "ranger", "rattle", "xgboost")
for(package in packages){
	print(paste(package, packageVersion(package)))
}

# update.packages()