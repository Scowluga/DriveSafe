# DriveSafe 
See the DriveSafe [video demo](https://www.youtube.com/watch?v=MB_7s3AYR5s).

According to the [government of canada](https://tc.canada.ca/en/road-transportation/motor-vehicle-safety/canadian-motor-vehicle-traffic-collision-statistics-2018), in 2018 alone, motor vehicle collisions were the cause of 152,847 injuries, 1,743 of which were fatal. DriveSafe is a database-reliant platform that acts to promote road safety, in hopes of lowering this number in the coming years. 

## Features 
DriveSafe supports an interactive map, similar to Google maps, with two main features: 
1. DriveSafe calculates the "safest" route for drivers using open source collision information and live traffic data. 
2. DriveSafe warns drivers when they are approaching dangerous intersections using text-to-speech. 

## Data 
DriveSafe leverages HERE.com's API for live traffic data and maps. Additionally, it uses: 
1. [An open source collision information dataset](https://public.tableau.com/profile/icbc#!/vizhome/LowerMainlandCrashes/LMDashboard)
2. [A public traffic volume dataset](https://opendata.vancouver.ca/explore/dataset/intersection-traffic-movement-counts/table/)

## Running the App 
The Android app requires proper license keys for the HERE.com Android API. See [HERE.com](https://developer.here.com/) for more information on how to set this up. 

Unfortunately, the MySQL database for DriveSafe is no longer being hosted, so the rest of the functionality will not work. 
