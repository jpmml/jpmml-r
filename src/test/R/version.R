print("R version:")
print(paste(R.version$major, R.version$minor, sep = "."))

latest_versions = available.packages()

print("R package versions:")
packages = c("caret", "e1071", "earth", "gbm", "IsolationForest", "party", "pls", "randomForest", "ranger", "rattle", "rms", "xgboost")
for(package in packages){
	version = packageVersion(package)
	if(!(package %in% c("IsolationForest"))){
		latest_version = latest_versions[package, "Version"]
	} else {
		latest_version = "NA"
	}

	print(paste(package, version, latest_version))
}

# update.packages()