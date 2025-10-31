/**
 * Table Column Resizer
 * Makes table columns resizable by dragging column borders
 */
(function() {
    'use strict';

    function makeTableResizable(table) {
        if (!table) return;

        // Create a container div if table doesn't have one
        let container = table.parentElement;
        if (!container.classList.contains('table-responsive')) {
            const wrapper = document.createElement('div');
            wrapper.className = 'table-responsive';
            table.parentNode.insertBefore(wrapper, table);
            wrapper.appendChild(table);
            container = wrapper;
        }

        // Add resizable class to table
        table.classList.add('resizable-table', 'table-adjustable');

        const ths = table.querySelectorAll('thead th');
        let currentTh = null;
        let startX = 0;
        let startWidth = 0;

        ths.forEach((th, index) => {
            // Skip first column (checkbox) and last column (actions) if they should not be resizable
            if (th.querySelector('.form-check') || th.classList.contains('no-resize')) {
                return;
            }

            // Make column resizable
            th.classList.add('resizable');
            
            const resizeHandle = document.createElement('div');
            resizeHandle.className = 'resize-handle';
            resizeHandle.style.cssText = `
                position: absolute;
                top: 0;
                right: -3px;
                width: 6px;
                height: 100%;
                cursor: col-resize;
                background: transparent;
                z-index: 2;
            `;
            th.style.position = 'relative';
            th.appendChild(resizeHandle);

            // Mouse down event
            resizeHandle.addEventListener('mousedown', function(e) {
                e.preventDefault();
                e.stopPropagation();
                
                currentTh = th;
                startX = e.pageX;
                startWidth = th.offsetWidth;

                document.addEventListener('mousemove', handleMouseMove);
                document.addEventListener('mouseup', handleMouseUp);
                
                document.body.style.cursor = 'col-resize';
                document.body.style.userSelect = 'none';
            });
        });

        function handleMouseMove(e) {
            if (!currentTh) return;
            
            const diff = e.pageX - startX;
            const newWidth = startWidth + diff;
            
            // Minimum column width
            if (newWidth > 50) {
                currentTh.style.width = newWidth + 'px';
                currentTh.style.minWidth = newWidth + 'px';
                
                // Update corresponding cells in tbody
                const columnIndex = Array.from(currentTh.parentElement.children).indexOf(currentTh);
                const rows = table.querySelectorAll('tbody tr');
                rows.forEach(row => {
                    const cell = row.children[columnIndex];
                    if (cell) {
                        cell.style.width = newWidth + 'px';
                        cell.style.minWidth = newWidth + 'px';
                    }
                });
            }
        }

        function handleMouseUp() {
            currentTh = null;
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }
    }

    // Auto-initialize on page load
    document.addEventListener('DOMContentLoaded', function() {
        // Find all tables and make them resizable
        const tables = document.querySelectorAll('table.table, table.table-hover');
        tables.forEach(table => {
            // Skip tables that already have resizable class
            if (!table.classList.contains('resizable-table')) {
                makeTableResizable(table);
            }
        });
    });

    // Export function for manual initialization
    window.makeTableResizable = makeTableResizable;
})();

