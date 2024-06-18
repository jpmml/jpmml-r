library("apollo")

source("util.R")

apollo_initialise()

predictChoiceMode = function(model, apollo_probabilities, apollo_inputs){
	choices = c("car", "bus", "air", "rail")

	choice = apollo_prediction(model, apollo_probabilities, apollo_inputs)
	choice = choice[choices]
	colnames(choice) = lapply(choices, function(x){ paste("probability(", x, ")", sep = "")})
	choice$choice = apply(choice, 1, function(x) { choices[which.max(x)] })

	return (choice)
}

# See http://www.apollochoicemodelling.com/files/examples/1%20MNL/MNL_SP.r
generateMNLModeChoice = function(){
	database <<- loadModeChoiceCsv("ModeChoice")

	apollo_control <<- list(
		modelName       = "MNL_SP",
		modelDescr      = "Simple MNL model on mode choice SP data",
		indivID         = "ID",
		outputDirectory = "/tmp"
	)

	apollo_beta <<- c(
		asc_car      = 0,
		asc_bus      = 0,
		asc_air      = 0,
		asc_rail     = 0,
		b_tt_car     = 0,
		b_tt_bus     = 0,
		b_tt_air     = 0,
		b_tt_rail    = 0,
		b_access     = 0,
		b_cost       = 0,
		b_no_frills  = 0,
		b_wifi       = 0,
		b_food       = 0
	)

	apollo_fixed <<- c("asc_car","b_no_frills")

	apollo_inputs = apollo_validateInputs()

	apollo_probabilities=function(apollo_beta, apollo_inputs, functionality="estimate"){

		### Attach inputs and detach after function exit
		apollo_attach(apollo_beta, apollo_inputs)
		on.exit(apollo_detach(apollo_beta, apollo_inputs))

		### Create list of probabilities P
		P = list()

		### List of utilities: these must use the same names as in mnl_settings, order is irrelevant
		V = list()
		V[["car"]]  = asc_car  + b_tt_car  * time_car                           + b_cost * cost_car
		V[["bus"]]  = asc_bus  + b_tt_bus  * time_bus  + b_access * access_bus  + b_cost * cost_bus 
		V[["air"]]  = asc_air  + b_tt_air  * time_air  + b_access * access_air  + b_cost * cost_air    + b_no_frills * ( service_air == 1 )  + b_wifi * ( service_air == 2 )  + b_food * ( service_air == 3 )
		V[["rail"]] = asc_rail + b_tt_rail * time_rail + b_access * access_rail + b_cost * cost_rail   + b_no_frills * ( service_rail == 1 ) + b_wifi * ( service_rail == 2 ) + b_food * ( service_rail == 3 )

		### Define settings for MNL model component
		mnl_settings = list(
			alternatives  = c(car=1, bus=2, air=3, rail=4), 
			avail         = list(car=av_car, bus=av_bus, air=av_air, rail=av_rail), 
			choiceVar     = choice,
			utilities     = V
		)

		### Compute probabilities using MNL model
		P[["model"]] = apollo_mnl(mnl_settings, functionality)

		### Take product across observation for same individual
		P = apollo_panelProd(P, apollo_inputs, functionality)

		### Prepare and return outputs of function
		P = apollo_prepareProb(P, apollo_inputs, functionality)
		return(P)
	}

	model = apollo_estimate(apollo_beta, apollo_fixed, apollo_probabilities, apollo_inputs)
	print(model)

	choice = predictChoiceMode(model, apollo_probabilities, apollo_inputs)

	storeRds(model, "MNLModeChoice")
	storeCsv(choice, "MNLModeChoice")
}

# See http://www.apollochoicemodelling.com/files/examples/2%20GEV/NL_three_levels.r
generateNLModeChoice = function(){
	database <<- loadModeChoiceCsv("ModeChoice")

	apollo_control <<- list(
		modelName       = "NL_three_levels",
		modelDescr      = "Three-level NL model with socio-demographics on mode choice SP data",
		indivID         = "ID", 
		outputDirectory = "/tmp"
	)

	apollo_beta <<- c(
		asc_car               = 0,
		asc_bus               = 0,
		asc_air               = 0,
		asc_rail              = 0,
		asc_bus_shift_female  = 0,
		asc_air_shift_female  = 0,
		asc_rail_shift_female = 0,
		b_tt_car              = 0,
		b_tt_bus              = 0,
		b_tt_air              = 0,
		b_tt_rail             = 0,
		b_tt_shift_business   = 0,
		b_access              = 0,
		b_cost                = 0,
		b_cost_shift_business = 0,
		cost_income_elast     = 0,
		b_no_frills           = 0,
		b_wifi                = 0,
		b_food                = 0,
		lambda_PT             = 1,
		lambda_fastPT         = 1
	)

	apollo_fixed <<- c("asc_car","b_no_frills")

	apollo_inputs = apollo_validateInputs()

	apollo_probabilities=function(apollo_beta, apollo_inputs, functionality="estimate"){

		### Attach inputs and detach after function exit
		apollo_attach(apollo_beta, apollo_inputs)
		on.exit(apollo_detach(apollo_beta, apollo_inputs))

		### Create list of probabilities P
		P = list()

		### Create alternative specific constants and coefficients using interactions with socio-demographics
		mean_income = 44748.27

		asc_bus_value   = asc_bus  + asc_bus_shift_female * female
		asc_air_value   = asc_air  + asc_air_shift_female * female
		asc_rail_value  = asc_rail + asc_rail_shift_female * female
		b_tt_car_value  = b_tt_car + b_tt_shift_business * business
		b_tt_bus_value  = b_tt_bus + b_tt_shift_business * business
		b_tt_air_value  = b_tt_air + b_tt_shift_business * business
		b_tt_rail_value = b_tt_rail + b_tt_shift_business * business
		b_cost_value    = ( b_cost +  b_cost_shift_business * business ) * ( income / mean_income ) ^ cost_income_elast

		### List of utilities: these must use the same names as in nl_settings, order is irrelevant
		V = list()
		V[["car"]]  = asc_car        + b_tt_car_value  * time_car                           + b_cost_value * cost_car
		V[["bus"]]  = asc_bus_value  + b_tt_bus_value  * time_bus  + b_access * access_bus  + b_cost_value * cost_bus 
		V[["air"]]  = asc_air_value  + b_tt_air_value  * time_air  + b_access * access_air  + b_cost_value * cost_air   + b_no_frills * ( service_air == 1 )  + b_wifi * ( service_air == 2 )  + b_food * ( service_air == 3 )
		V[["rail"]] = asc_rail_value + b_tt_rail_value * time_rail + b_access * access_rail + b_cost_value * cost_rail  + b_no_frills * ( service_rail == 1 ) + b_wifi * ( service_rail == 2 ) + b_food * ( service_rail == 3 )

		### Specify nests for NL model
		nlNests      = list(root=1, PT=lambda_PT, fastPT=lambda_fastPT)

		### Specify tree structure for NL model
		nlStructure = list()
		nlStructure[["root"]]   = c("car","PT")
		nlStructure[["PT"]]     = c("bus","fastPT")
		nlStructure[["fastPT"]] = c("air","rail")

		### Define settings for NL model
		nl_settings <- list(
			alternatives = c(car=1, bus=2, air=3, rail=4),
			avail        = list(car=av_car, bus=av_bus, air=av_air, rail=av_rail),
			choiceVar    = choice,
			utilities    = V,
			nlNests      = nlNests,
			nlStructure  = nlStructure
		)

		### Compute probabilities using NL model
		P[["model"]] = apollo_nl(nl_settings, functionality)

		### Take product across observation for same individual
		P = apollo_panelProd(P, apollo_inputs, functionality)

		### Prepare and return outputs of function
		P = apollo_prepareProb(P, apollo_inputs, functionality)
		return(P)
	}

	model = apollo_estimate(apollo_beta, apollo_fixed, apollo_probabilities, apollo_inputs)
	print(model)

	choice = predictChoiceMode(model, apollo_probabilities, apollo_inputs)

	storeRds(model, "NLModeChoice")
	storeCsv(choice, "NLModeChoice")
}

set.seed(42)

generateMNLModeChoice()

rm(list = c("database", "apollo_control", "apollo_beta", "apollo_fixed"))

set.seed(42)

generateNLModeChoice()
