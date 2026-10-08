const express = require('express');
const app = express();
app.use(express.json());

let latestLocations = [];

// Endpoint to receive GPS points batch from Android app
app.post('/api/locations', (req, res) => {
  const { locations } = req.body;
  if (locations && Array.isArray(locations)) {
    latestLocations = locations;
    console.log(`[FASTER 4G] Received ${locations.length} location points.`);
    res.status(200).json({ success: true, received: locations.length });
  } else {
    res.status(400).json({ success: false, error: 'Invalid data format' });
  }
});

// Endpoint to view locations in browser
app.get('/api/locations', (req, res) => {
  res.json({
    total: latestLocations.length,
    locations: latestLocations
  });
});

app.get('/', (req, res) => {
  res.send('FASTER 4G Taxi GPS Server is running successfully!');
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
