# CryptocurrencyMC Documentation Wiki

This directory contains the complete documentation website for the CryptocurrencyMC plugin.

## 📚 Documentation Pages

- **[index.html](index.html)** - Home page with plugin overview and features
- **[installation.html](installation.html)** - Complete installation guide
- **[commands.html](commands.html)** - Full command reference
- **[permissions.html](permissions.html)** - Permission nodes and setup
- **[configuration.html](configuration.html)** - Configuration guide
- **[api.html](api.html)** - Developer API and integration guide
- **[faq.html](faq.html)** - Frequently asked questions

## 🎨 Design

The documentation uses a modern, responsive design with:
- Dark theme optimized for readability
- Bitcoin/crypto themed color scheme (gold and blue)
- Clean typography and layout
- Mobile-friendly responsive design
- Smooth animations and transitions

## 🌐 Viewing the Documentation

### Local Viewing
Simply open `index.html` in your web browser to view the documentation locally.

### Hosting Options

#### GitHub Pages
1. Push this repository to GitHub
2. Go to repository Settings → Pages
3. Set source to main branch, `/docs` folder
4. Your documentation will be available at `https://yourusername.github.io/CryptocurrencyMC/`

#### Web Server
Upload the `docs/` folder to any web server and access via HTTP.

#### Simple HTTP Server
```bash
cd docs
python -m http.server 8000
# Then visit http://localhost:8000
```

## 📝 Customization

### Styling
All styles are in `css/style.css`. Key CSS variables:
- `--primary-color`: Main accent color (Bitcoin orange)
- `--secondary-color`: Secondary accent (Blue)
- `--dark-bg`: Background color
- `--text-color`: Main text color

### Content
Edit the HTML files directly to update content. All pages use the same navigation structure and styling.

## 🔗 Navigation

Each page includes a consistent navigation bar with links to all documentation sections. The active page is highlighted.

## 📱 Responsive Design

The documentation is fully responsive and works on:
- Desktop (1200px+)
- Tablet (768px - 1199px)
- Mobile (< 768px)

## 🎯 Features

- **Semantic HTML5** for better accessibility
- **CSS Grid and Flexbox** for modern layouts
- **Color-coded information** (success, warning, info alerts)
- **Code syntax highlighting** with custom styles
- **Interactive tables** with hover effects
- **Smooth animations** for better UX
- **Print-friendly** styling (automatically applied)

## 📖 Documentation Standards

- All code examples use proper syntax highlighting
- Commands show required vs optional parameters
- Examples are provided for all features
- Clear navigation structure
- Consistent formatting throughout

## 🚀 Future Enhancements

Potential improvements:
- Search functionality
- Interactive examples
- Video tutorials
- Multi-language support
- PDF export option
- Dark/light theme toggle

## 📄 License

This documentation is part of the CryptocurrencyMC plugin and follows the same license.

---

**Note**: This is a static HTML/CSS website with no build process required. Just open in a browser or host on any web server.
